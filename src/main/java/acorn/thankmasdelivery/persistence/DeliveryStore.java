package acorn.thankmasdelivery.persistence;

import acorn.thankmasdelivery.effect.DeliveryEffectRules;
import acorn.thankmasdelivery.model.DeliveryIdentity;
import acorn.thankmasdelivery.model.DeliveryOfferSummary;
import acorn.thankmasdelivery.model.DeliveryOption;
import acorn.thankmasdelivery.npc.DeliveryDialogue.Outcome;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Saves player scores and recipient settings. Active deliveries and parcel choices
 * information and statuses cleared when the player leaves or the server restarts.
 * DeliveryActions saves files in the background
 */
public final class DeliveryStore {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public record Offers(UUID token, List<DeliveryOption> options) {
        public Offers {
            options = List.copyOf(options);
            if (token == null || options.size() != 3) {
                throw new IllegalArgumentException("Invalid offers");
            }
        }
        public List<DeliveryOfferSummary> summaries() {
            List<DeliveryOfferSummary> summaries = new ArrayList<>();
            for (int i = 0; i < options.size(); i++) {
                summaries.add(DeliveryOfferSummary.of(i + 1, options.get(i)));
            }
            return List.copyOf(summaries);
        }
    }
    public record Destination(String id, String name, String world, double x, double y, double z, String hint, UUID entityId, UUID worldId, boolean disabled, String celebrationEmote) {
        public Destination {
            if (id == null || !id.matches("[a-z0-9_-]{1,64}") || name == null || name.isBlank()
                || world == null || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid recipient");
            }
            Objects.requireNonNull(entityId);
            Objects.requireNonNull(worldId);
            hint = Objects.requireNonNull(hint).strip();
            celebrationEmote = Objects.requireNonNull(celebrationEmote).strip();
        }
        public Destination(String id, String name, String world, double x, double y, double z, String hint, UUID entityId, UUID worldId, boolean disabled) {
            this(id, name, world, x, y, z, hint, entityId, worldId, disabled, "");
        }
    }
    public record Run(DeliveryIdentity identity, DeliveryOption option, Destination recipient, boolean testMode) {
        public Run(DeliveryIdentity identity, DeliveryOption option, Destination recipient) {
            this(identity, option, recipient, false);
        }
        public Run {
            Objects.requireNonNull(identity);
            Objects.requireNonNull(option);
            Objects.requireNonNull(recipient);
        }
    }
    public record Profile(UUID owner, String name, boolean talked, Outcome outcome, long score,
        Run active, UUID lastFinished, String notice) {
        public Profile {
            Objects.requireNonNull(owner); Objects.requireNonNull(name); Objects.requireNonNull(outcome);
            if (score < 0 || (active != null && !owner.equals(active.identity.ownerId()))) {
                throw new IllegalArgumentException("Invalid player record");
            }
        }
        private Profile withRun(Run run) {
            return new Profile(owner, name, talked, outcome, score, run, lastFinished, notice);
        }
    }
    private record Snapshot(int version, Map<UUID, Profile> players, Map<String, Destination> recipients, long revision) {
        Snapshot(int version, Map<UUID, Profile> players, Map<String, Destination> recipients) {
            this(version, players, recipients, 0);
        }
    }
    private record Change(long revision, Map<UUID, Profile> players, Map<String, Destination> recipients,
        Set<String> removedRecipients) { }
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "thankmasDelivery-save"));
    private static final Gson JOURNAL_JSON = new Gson();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private int writesSinceCheckpoint;
    private boolean journalPoisoned;

    public <T> CompletableFuture<T> submit(Supplier<T> action) {
        return CompletableFuture.supplyAsync(action, writer);
    }
    public void close() {
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                LOGGER.atSevere().log("Delivery writer did not finish within 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            sessionOffers.clear();
            sessionProfiles.clear();
        }
    }
    private final Path file;
    private volatile Snapshot state;
    // Saved history never contains a live delivery. These maps contain only this server session.
    private final Map<UUID, Offers> sessionOffers = new HashMap<>();
    private final ConcurrentMap<UUID, Profile> sessionProfiles = new ConcurrentHashMap<>();

    /** Loads saved progress, then applies any newer changes from file. */
    public DeliveryStore(Path file) {
        this.file = file.toAbsolutePath();
        try {
            Snapshot best = null;
            Path selected = null;
            boolean found = false;
            for (Path candidate : List.of(this.file, sibling(".bak"), sibling(".tmp"))) {
                if (!Files.exists(candidate)) {
                    continue;
                }
                found = true;
                try {
                    Snapshot snapshot = readSnapshot(candidate);
                    if (best == null || snapshot.revision > best.revision) {
                        best = snapshot;
                        selected = candidate;
                    }
                } catch (RuntimeException | IOException invalid) {
                    LOGGER.atWarning().withCause(invalid).log("Invalid delivery snapshot: %s", candidate);
                }
            }
            if (best == null && found) {
                throw new IOException("No valid delivery snapshot; restore a matching backup");
            }
            state = best == null ? new Snapshot(1, Map.of(), Map.of()) : best;
            if (selected != null && !selected.equals(this.file)) {
                LOGGER.atSevere().log("Recovering delivery snapshot from %s at revision %s. Check matching world backup; recent progress may have rolled back.", selected, state.revision);
            }
            replayJournal();
            discardLegacyDeliveries();
            // Checkpoint before accepting new score changes.
            checkpoint();
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private void discardLegacyDeliveries() {
        Map<UUID, Profile> players = new HashMap<>(state.players);
        for (Profile p : state.players.values()) {
            players.put(p.owner, new Profile(p.owner, p.name, p.talked,
                p.active == null ? p.outcome : Outcome.ABANDONED, p.score, null, p.lastFinished, null));
        }
        state = nextState(Map.copyOf(players), state.recipients);
    }

    private Snapshot readSnapshot(Path source) throws IOException {
        Snapshot snapshot = JSON.fromJson(Files.readString(source), Snapshot.class);
        validate(snapshot);
        return snapshot;
    }

    private static void validate(Snapshot snapshot) throws IOException {
        if (snapshot == null || snapshot.version != 1 || snapshot.players == null || snapshot.recipients == null || snapshot.revision < 0) {
            throw new IOException("Unsupported or invalid delivery snapshot");
        }
        for (Entry<UUID, Profile> e : snapshot.players.entrySet()) {
            Profile p = e.getValue();
            if (p == null || !e.getKey().equals(p.owner)) {
                throw new IOException("Mismatched player record");
            }
        }
        for (Entry<String, Destination> e : snapshot.recipients.entrySet()) {
            if (e.getValue() == null || !e.getKey().equals(e.getValue().id)) {
                throw new IOException("Mismatched recipient record");
            }
        }
    }

    private Path sibling(String suffix) {
        return file.resolveSibling(file.getFileName() + suffix);
    }

    private void replayJournal() throws IOException {
        Path journal = sibling(".journal");
        if (!Files.exists(journal)) {
            return;
        }
        String data = Files.readString(journal);
        int complete = data.lastIndexOf('\n') + 1;
        if (complete != data.length()) {
            LOGGER.atWarning().log("Ignoring unfinished trailing delivery journal write");
        }
        for (String line : data.substring(0, complete).split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            final Change change;
            try { change = JOURNAL_JSON.fromJson(line, Change.class); }
            catch (RuntimeException invalid) {
                throw new IOException("Corrupt delivery journal; restore a matching backup", invalid);
            }
            if (change == null || change.revision < 1 || change.players == null || change.recipients == null || change.removedRecipients == null) {
                throw new IOException("Invalid delivery journal record");
            }
            if (change.revision <= state.revision) {
                continue;
            }
            if (change.revision != state.revision + 1) {
                throw new IOException("Gap in delivery journal revisions");
            }
            Map<UUID, Profile> players = new HashMap<>(state.players); players.putAll(change.players);
            Map<String, Destination> recipients = new HashMap<>(state.recipients); recipients.putAll(change.recipients);
            change.removedRecipients.forEach(recipients::remove);
            Snapshot next = new Snapshot(1, Map.copyOf(players), Map.copyOf(recipients), change.revision);
            validate(next); state = next;
        }
    }

    public Profile profile(UUID owner, String name) {
        Profile profile = sessionProfiles.get(owner);
        if (profile == null) {
            profile = state.players.get(owner);
        }
        return profile != null ? profile : new Profile(owner, name, false, Outcome.NONE, 0, null, null, null);
    }

    public synchronized void talked(UUID owner, String name) {
        Profile p = profile(owner, name);
        saveProfile(new Profile(owner, name, true, p.outcome, p.score, p.active, p.lastFinished, p.notice));
    }

    public synchronized Offers offers(UUID owner, String name) {
        Profile p = profile(owner, name);
        if (p.active != null) {
            throw new IllegalStateException("Finish your current delivery first.");
        }
        if (sessionOffers.containsKey(owner)) {
            return sessionOffers.get(owner);
        }
        if (state.recipients.isEmpty()) {
            throw new IllegalStateException("No recipients are configured yet.");
        }
        Offers offers = new Offers(UUID.randomUUID(), DeliveryOption.randomChoices(ThreadLocalRandom.current()));
        sessionOffers.put(owner, offers);
        return offers;
    }

    /** Immediately cancels a player's session without waiting for disk or the save executor. */
    public synchronized void leave(UUID owner) {
        sessionOffers.remove(owner);
        sessionProfiles.remove(owner);
    }

    public synchronized Run begin(UUID owner, String name, UUID offerToken, int index, Set<String> eligible) {
        Profile p = profile(owner, name);
        Offers offers = sessionOffers.get(owner);
        if (p.active != null || offers == null || !offers.token.equals(offerToken) || index < 1 || index > DeliveryOption.CHOICE_COUNT) {
            throw new IllegalStateException("Those parcel choices are out of date. Talk to the dispatcher again.");
        }
        Destination recipient = randomRecipient(eligible);
        Run run = new Run(new DeliveryIdentity(owner, UUID.randomUUID()), offers.options.get(index - 1), recipient);
        remember(owner, p.withRun(run));
        sessionOffers.remove(owner);
        return run;
    }

    /** Starts an admin test that uses normal delivery rules but never awards points. */
    public synchronized Run beginTest(UUID owner, String name, DeliveryOption option, Set<String> eligible) {
        Profile p = profile(owner, name);
        if (p.active != null) {
            throw new IllegalStateException("Stop your current delivery with /parcel stop before starting a test.");
        }
        if (!DeliveryEffectRules.canOffer(option.effects())) {
            throw new IllegalArgumentException("These effects cannot be combined.");
        }
        Destination recipient = randomRecipient(eligible);
        Run run = new Run(new DeliveryIdentity(owner, UUID.randomUUID()), option, recipient, true);
        remember(owner, p.withRun(run));
        return run;
    }

    private Destination randomRecipient(Set<String> eligible) {
        // Usually only a few configured NPCs are loaded in the courier's world.
        List<Destination> reachable = eligible.stream().map(state.recipients::get)
            .filter(Objects::nonNull).filter(destination -> !destination.disabled).toList();
        if (reachable.isEmpty()) {
            throw new IllegalStateException("No reachable recipients are available in this world.");
        }
        return reachable.get(ThreadLocalRandom.current().nextInt(reachable.size()));
    }

    public synchronized boolean finishAtRecipient(UUID owner, String name, UUID assignment, String recipient) {
        Profile p = profile(owner, name);
        if (p.active == null || !p.active.recipient.id.equals(recipient)) {
            return false;
        }
        Destination current = state.recipients.get(recipient);
        if (current == null || current.disabled) {
            return false;
        }
        return finish(owner, name, assignment, Outcome.COMPLETED);
    }

    public synchronized boolean finish(UUID owner, String name, UUID assignment, Outcome outcome) {
        return finish(owner, name, assignment, outcome, null);
    }
    public synchronized boolean finish(UUID owner, String name, UUID assignment, Outcome outcome, String notice) {
        if (outcome == Outcome.NONE) {
            throw new IllegalArgumentException("A delivery must have a final outcome");
        }
        Profile p = profile(owner, name);
        if (p.active == null || !p.active.identity.assignmentId().equals(assignment)) {
            return false;
        }
        long score = outcome == Outcome.COMPLETED && !p.active.testMode ? Math.addExact(p.score, p.active.option.points()) : p.score;
        Profile ended = new Profile(owner, name, p.talked, outcome, score, null, assignment, notice);
        if (outcome == Outcome.ABANDONED) {
            remember(owner, ended);
        } else {
            saveProfile(ended);
        }
        return true;
    }

    private void remember(UUID owner, Profile profile) {
        // Starting, dropping, and cancelling parcels must not copy every player's saved history.
        sessionProfiles.put(owner, profile);
    }

    private static Profile persistentProfile(Profile p) {
        return new Profile(p.owner, p.name, p.talked, p.outcome, p.score, null, p.lastFinished, null);
    }

    private Snapshot nextState(Map<UUID, Profile> players, Map<String, Destination> recipients) {
        return new Snapshot(1, players, recipients, state.revision);
    }

    public synchronized void register(Destination destination) {
        if (destination.equals(state.recipients.get(destination.id))) {
            return;
        }
        Map<String, Destination> recipients = new HashMap<>(state.recipients);
        recipients.put(destination.id, destination);
        commit(nextState(state.players, Map.copyOf(recipients)));
    }

    public synchronized int removeRecipient(String id) {
        return endRecipient(id, true);
    }

    public synchronized Destination recipient(String id) {
        return state.recipients.get(id);
    }

    public synchronized Destination setLocationHint(String id, String hint) {
        Destination recipient = state.recipients.get(id);
        if (recipient == null) {
            throw new IllegalStateException("No recipient is registered with ID " + id + ".");
        }
        hint = hint.strip();
        if (hint.length() >= 2 && ((hint.startsWith("\"") && hint.endsWith("\""))
            || (hint.startsWith("'") && hint.endsWith("'")))) {
            hint = hint.substring(1, hint.length() - 1).strip();
        }
        Destination updated = new Destination(recipient.id, recipient.name, recipient.world,
            recipient.x, recipient.y, recipient.z, hint, recipient.entityId, recipient.worldId, recipient.disabled, recipient.celebrationEmote);
        register(updated);
        return updated;
    }

    public synchronized Destination setCelebrationEmote(String id, String emote) {
        Destination recipient = state.recipients.get(id);
        if (recipient == null) {
            throw new IllegalStateException("No recipient is registered with ID " + id + ".");
        }
        Destination updated = new Destination(recipient.id, recipient.name, recipient.world, recipient.x, recipient.y, recipient.z,
            recipient.hint, recipient.entityId, recipient.worldId, recipient.disabled, emote);
        register(updated);
        return updated;
    }

    private void saveProfile(Profile next) {
        UUID owner = next.owner;
        Profile saved = persistentProfile(next);
        if (!saved.equals(state.players.get(owner))) {
            Map<UUID, Profile> players = new HashMap<>(state.players);
            players.put(owner, saved);
            commit(nextState(Map.copyOf(players), state.recipients));
        }
        // Publish the live view only after the durable write succeeds.
        remember(owner, next);
    }

    public synchronized String takeNotice(UUID owner, String name) {
        Profile p = profile(owner, name);
        if (p.notice == null) {
            return null;
        }
        remember(owner, new Profile(owner, name, p.talked, p.outcome, p.score, p.active, p.lastFinished, null));
        return p.notice;
    }

    public List<Profile> assignmentsFor(String id) {
        return sessionProfiles.values().stream().filter(p -> p.active != null && p.active.recipient.id.equals(id)).toList();
    }

    /** Disables a recipient and abandons its deliveries without points. */
    public synchronized int retire(String id) {
        return endRecipient(id, false);
    }

    private int endRecipient(String id, boolean remove) {
        Destination old = state.recipients.get(id);
        if (old == null) {
            if (remove) {
                return 0;
            }
            throw new IllegalStateException("No recipient is registered with ID " + id + ".");
        }
        Map<String, Destination> recipients = new HashMap<>(state.recipients);
        if (remove) {
            recipients.remove(id);
        } else {
            recipients.put(id, new Destination(old.id, old.name, old.world, old.x, old.y, old.z,
                old.hint, old.entityId, old.worldId, true, old.celebrationEmote));
        }
        List<Profile> affected = assignmentsFor(id);
        // Keep the NPC change and the visible game state unchanged if its save fails.
        commit(nextState(state.players, Map.copyOf(recipients)));
        for (Profile p : affected) {
            finish(p.owner, p.name, p.active.identity.assignmentId(), Outcome.ABANDONED,
                "Your delivery ended because its recipient was removed. No points awarded.");
        }
        return affected.size();
    }

    // Append and flush the change before publishing it. A failed save leaves the visible score unchanged.
    private void commit(Snapshot proposed) {
        if (journalPoisoned) {
            throw new IllegalStateException("Delivery storage requires restart/repair after an incomplete failed write.");
        }
        long start = System.nanoTime();
        Snapshot next = new Snapshot(1, proposed.players, proposed.recipients, state.revision + 1);
        Map<UUID, Profile> players = new HashMap<UUID, Profile>();
        next.players.forEach((id, p) -> { if (!p.equals(state.players.get(id))) {
                players.put(id, p);
            } });
        Map<String, Destination> recipients = new HashMap<String, Destination>();
        next.recipients.forEach((id, d) -> { if (!d.equals(state.recipients.get(id))) {
                recipients.put(id, d);
            } });
        Set<String> removed = new HashSet<>(state.recipients.keySet()); removed.removeAll(next.recipients.keySet());
        // Journal entries contain only changed records; checkpoints contain the complete saved state.
        byte[] bytes = (JOURNAL_JSON.toJson(new Change(next.revision, players, recipients, removed)) + "\n").getBytes(StandardCharsets.UTF_8);
        Path journal = sibling(".journal");
        try {
            Files.createDirectories(file.getParent());
            try (FileChannel channel = FileChannel.open(journal, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
                long originalSize = channel.size();
                channel.position(originalSize);
                try {
                    writeAll(channel, bytes);
                    channel.force(true);
                } catch (IOException failed) {
                    try {
                        channel.truncate(originalSize);
                        channel.force(true);
                    } catch (IOException rollbackFailed) {
                        journalPoisoned = true;
                        failed.addSuppressed(rollbackFailed);
                    }
                    throw failed;
                }
            }
            state = next;
        } catch (IOException error) {
            throw new UncheckedIOException("Could not save parcel delivery progress", error);
        } finally {
            double writeMillis = (System.nanoTime() - start) / 1_000_000.0;
            if (writeMillis > 100) {
                LOGGER.atWarning().log("Slow delivery journal write: %s ms", Math.round(writeMillis));
            }
        }
        if (++writesSinceCheckpoint >= 128) {
            try {
                checkpoint();
                writesSinceCheckpoint = 0;
            } catch (IOException error) {
                LOGGER.atWarning().withCause(error).log("Delivery checkpoint failed; durable journal retained");
            }
        }
    }

    private static void writeAll(FileChannel channel, byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private void writeSnapshot(Path target, Snapshot snapshot) throws IOException {
        Path temporary = target.resolveSibling(target.getFileName() + ".writing");
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            writeAll(channel, JSON.toJson(snapshot).getBytes(StandardCharsets.UTF_8)); channel.force(true);
        }
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void checkpoint() throws IOException {
        Files.createDirectories(file.getParent());
        // Both snapshots must reach disk before old journal entries can be discarded.
        writeSnapshot(sibling(".bak"), state);
        writeSnapshot(file, state);
        try (FileChannel channel = FileChannel.open(sibling(".journal"), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            channel.force(true);
        }
    }
}
