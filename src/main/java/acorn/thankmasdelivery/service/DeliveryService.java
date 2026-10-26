package acorn.thankmasdelivery.service;

import acorn.thankmasdelivery.asset.DeliveryAssets;
import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.component.DeliveryOwnership;
import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.model.DeliveryIdentity;
import acorn.thankmasdelivery.model.DeliveryOption;
import acorn.thankmasdelivery.model.DeliverySession;
import acorn.thankmasdelivery.npc.DeliveryCelebrationEmotes;
import acorn.thankmasdelivery.npc.DeliveryDialogue;
import acorn.thankmasdelivery.npc.DeliveryDialogue.Outcome;
import acorn.thankmasdelivery.persistence.DeliveryStore;
import acorn.thankmasdelivery.persistence.DeliveryStore.Destination;
import acorn.thankmasdelivery.persistence.DeliveryStore.Profile;
import acorn.thankmasdelivery.persistence.DeliveryStore.Run;
import acorn.thankmasdelivery.rules.DeliveryHandoffRules;
import acorn.thankmasdelivery.rules.DeliverySeparationRules;
import acorn.thankmasdelivery.util.DeliveryMessages;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.EmoteAsset;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs the delivery game: conversations, assignment changes, scoring, and departure.
 * Player/entity changes run on their world thread. Only durable saves go to DeliveryActions.
 * A player lock keeps disconnects from racing a start or a score save.
 */
public final class DeliveryService {
    public static final String PRESENT_BLOCK_ID = "Parcel";
    private static final long RETRY_INTERVAL_SECONDS = 5;
    private static final long MISSING_PARCEL_GRACE_NANOS = TimeUnit.SECONDS.toNanos(2);
    private final ComponentType<EntityStore, DeliveryAssignmentState> assignmentType;
    private final ComponentType<EntityStore, ThrownPresent> thrownPresentType;
    private final ComponentType<EntityStore, DeliveryNpc> npcType;
    private final DeliveryStore deliveries;
    private final ConcurrentMap<UUID, DeliveryAssignment> activeAssignments = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, DispatcherConversation> conversations = new ConcurrentHashMap<>();
    // Block delivery actions while a player is leaving.
    private final Set<PlayerRef> departingPlayers = Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));

    // Each save owns a ticket. A callback from an old world/session cannot finish a newer action.
    private final ConcurrentMap<UUID, Object> pending = new ConcurrentHashMap<>();
    private final DeliveryParcels parcels;
    private final DeliveryActions actions;
    private final DeliveryRecipientRegistry recipients;
    private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "thankmasDelivery-retry"));
    private volatile boolean closing;

    public boolean reserveRecipient(String id) {
        return recipients.reserve(id);
    }
    public void releaseRecipient(String id) {
        recipients.release(id);
    }
    public <T> void adminAction(World world, PlayerRef player, Supplier<T> work, Consumer<T> done, Runnable failed) {
        adminAction(null, world, player, work, done, failed);
    }
    public <T> void adminAction(String recipientId, World world, PlayerRef player, Supplier<T> work, Consumer<T> done, Runnable failed) {
        actions.onWorld(work, world::execute, (result, error) -> {
            try {
                if (error != null) {
                    logFailure(error);
                    failed.run();
                    tell(player, errorMessage(error));
                } else {
                    done.accept(result);
                }
            } finally { if (recipientId != null) {
                    releaseRecipient(recipientId);
                } }
        }, stopped -> {
            if (recipientId != null) {
                releaseRecipient(recipientId);
            }
            logFailure(stopped);
        });
    }

    public void joinedWorld(PlayerRef player) {
        departingPlayers.remove(player);
    }

    private record DispatcherConversation(Ref<EntityStore> npc, UUID offerToken) { }

    public DeliveryService(
        ComponentType<EntityStore, DeliveryAssignmentState> assignmentType,
        ComponentType<EntityStore, ThrownPresent> thrownPresentType,
        ComponentType<ChunkStore, DeliveryOwnership> ownershipType,
        ComponentType<EntityStore, DeliveryNpc> npcType,
        DeliveryStore deliveries
    ) {
        this.assignmentType = assignmentType;
        this.thrownPresentType = thrownPresentType;
        this.parcels = new DeliveryParcels(assignmentType, thrownPresentType, ownershipType, activeAssignments, pending::containsKey);
        this.npcType = npcType;
        this.deliveries = deliveries;
        this.actions = new DeliveryActions(deliveries);
        this.recipients = new DeliveryRecipientRegistry(deliveries, actions, DeliveryService::logFailure);
        retries.scheduleWithFixedDelay(recipients::retryRetirements, RETRY_INTERVAL_SECONDS, RETRY_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public DeliveryStore deliveryStore() {
        return deliveries;
    }
    public ComponentType<EntityStore, DeliveryNpc> npcType() {
        return npcType;
    }
    public boolean hasActiveDelivery(UUID owner) {
        return activeAssignments.containsKey(owner);
    }

    /** Handles dispatcher and recipient interactions on the world thread. */
    public boolean queueNpcInteraction(Ref<EntityStore> player, Ref<EntityStore> npc) {
        if (!canInteractNpc(player, npc)) {
            return false;
        }
        Store<EntityStore> store = player.getStore();
        store.getExternalData().getWorld().execute(() -> {
            if (!canInteractNpc(player, npc)) {
                return;
            }
            PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
            DeliveryNpc marker = store.getComponent(npc, npcType);
            if (marker.dispatcher()) {
                talkToDispatcher(playerRef, npc);
            } else {
                String message = deliverTo(playerRef, npc);
                if (message != null) {
                    tell(playerRef, message);
                }
            }
        });
        return true;
    }

    public boolean canInteractNpc(Ref<EntityStore> player, Ref<EntityStore> npc) {
        if (!nearNpc(player, npc)) {
            return false;
        }
        Store<EntityStore> store = player.getStore();
        PlayerRef playerRef = store.getComponent(player, PlayerRef.getComponentType());
        DeliveryNpc marker = store.getComponent(npc, npcType);
        if (playerRef == null || marker == null || departingPlayers.contains(playerRef)
            || pending.containsKey(playerRef.getUuid())
            || store.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        DeliveryAssignment assignment = activeAssignments.get(playerRef.getUuid());
        return marker.dispatcher() || (marker.recipient() && assignment != null && assignment.session.isActive());
    }

    public boolean nearNpc(Ref<EntityStore> player, Ref<EntityStore> npc) {
        if (player == null || npc == null || !player.isValid() || !npc.isValid() || player.getStore() != npc.getStore()) {
            return false;
        }
        Store<EntityStore> store = player.getStore();
        TransformComponent a = store.getComponent(player, TransformComponent.getComponentType());
        TransformComponent b = store.getComponent(npc, TransformComponent.getComponentType());
        return a != null && b != null && DeliveryHandoffRules.inReach(a.getPosition().distanceSquared(b.getPosition()));
    }

    public void talkToDispatcher(PlayerRef player, Ref<EntityStore> npc) {
        if (!isDispatcherInReach(player, npc) || departingPlayers.contains(player)) {
            return;
        }
        World world = npc.getStore().getExternalData().getWorld();
        DeliveryNpc marker = npc.getStore().getComponent(npc, npcType);
        Profile profile = deliveries.profile(player.getUuid(), player.getUsername());
        String greeting = DeliveryDialogue.greeting(profile.talked(), profile.outcome(), profile.active() != null);
        boolean canOffer = profile.active() == null && !eligibleRecipients(world).isEmpty() && DeliveryAssets.missing().isEmpty();
        operation(player, world, () -> {
            deliveries.talked(player.getUuid(), player.getUsername());
            return canOffer ? deliveries.offers(player.getUuid(), player.getUsername()) : null;
        }, offers -> {
            if (!isDispatcherInReach(player, npc)) {
                return;
            }
            conversations.put(player.getUuid(), new DispatcherConversation(npc, offers == null ? null : offers.token()));
            tell(player, marker.name() + ": " + greeting);
            if (offers != null) {
                player.sendMessage(DeliveryMessages.withPrefix(DeliveryMessages.offerSummaries(offers.summaries())));
            } else {
                tell(player, profile.active() != null ? deliver(player) : unavailableReason(world));
            }
        });
    }

    private boolean isDispatcherInReach(PlayerRef player, Ref<EntityStore> npc) {
        if (!nearNpc(player.getReference(), npc)) {
            return false;
        }
        DeliveryNpc marker = npc.getStore().getComponent(npc, npcType);
        return marker != null && marker.dispatcher();
    }

    /** Shows parcel choices while the player is near the dispatcher. */
    public void choices(PlayerRef player) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        if (deliveries.profile(player.getUuid(), player.getUsername()).active() != null) {
            tell(player, deliver(player));
            return;
        }
        DispatcherConversation conversation = conversations.get(player.getUuid());
        if (conversation == null || !isDispatcherInReach(player, conversation.npc())) {
            conversations.remove(player.getUuid()); tell(player, "Talk to the Thankmas dispatcher with F to see your parcel choices."); return;
        }
        World world = ref.getStore().getExternalData().getWorld();
        String unavailable = unavailableReason(world);
        if (unavailable != null) {
            tell(player, unavailable);
            return;
        }
        operation(player, world, () -> deliveries.offers(player.getUuid(), player.getUsername()), offers -> {
            if (!isDispatcherInReach(player, conversation.npc())) {
                return;
            }
            conversations.put(player.getUuid(), new DispatcherConversation(conversation.npc(), offers.token()));
            player.sendMessage(DeliveryMessages.withPrefix(DeliveryMessages.offerSummaries(offers.summaries())));
        });
    }

    /** Starts a delivery with one of the dispatcher's parcel choices. */
    public void choose(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, int optionNumber) {
        if (pending.containsKey(player.getUuid())) {
            tell(player, "Your previous parcel action is still being saved. Please wait.");
            return;
        }
        DispatcherConversation conversation = conversations.get(player.getUuid());
        if (conversation == null || conversation.offerToken() == null || !isDispatcherInReach(player, conversation.npc())) {
            tell(player, "Talk to the Thankmas dispatcher with F and stay nearby to choose a parcel.");
            return;
        }
        if (optionNumber < 1 || optionNumber > 3) {
            tell(player, "Choose 1, 2, or 3.");
            return;
        }
        if (!prepareStart(store, ref, player, "choosing a parcel", "Finish or stop your current delivery first.")) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Set<String> eligible = eligibleRecipients(world);
        startRun(store, ref, player, world, () -> deliveries.begin(player.getUuid(), player.getUsername(), conversation.offerToken(), optionNumber, eligible), conversation.npc());
    }

    /** Starts a test delivery without talking to the dispatcher. */
    public void test(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, DeliveryOption option) {
        if (pending.containsKey(player.getUuid())) {
            tell(player, "Your previous parcel action is still being saved. Please wait.");
            return;
        }
        if (!prepareStart(store, ref, player, "starting a test parcel", "Stop your current delivery with /parcel stop before starting a test.")) {
            return;
        }
        World world = store.getExternalData().getWorld();
        Set<String> eligible = eligibleRecipients(world);
        startRun(store, ref, player, world, () -> deliveries.beginTest(player.getUuid(), player.getUsername(), option, eligible), null);
    }

    private void startRun(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world,
        Supplier<DeliveryStore.Run> create, Ref<EntityStore> dispatcher) {
        // Starting a delivery touches memory only, so equip it in the same world update.
        synchronized (player) {
            if (closing || departingPlayers.contains(player)) {
                return;
            }
            try {
                finishStartingRun(store, ref, player, world, create.get(), dispatcher);
            } catch (IllegalStateException invalid) {
                tell(player, invalid.getMessage());
            }
        }
    }

    private boolean prepareStart(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
        String action, String existingRunMessage) {
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            tell(player, "Respawn before " + action + ".");
            return false;
        }
        if (deliveries.profile(player.getUuid(), player.getUsername()).active() != null) {
            tell(player, existingRunMessage);
            return false;
        }
        parcels.clearStaleState(store, ref, player.getUuid());
        if (store.getComponent(ref, CarriedBlock.getComponentType()) != null) {
            tell(player, "Put down the block you are carrying before " + action + ".");
            return false;
        }
        String unavailable = unavailableReason(store.getExternalData().getWorld());
        if (unavailable != null) {
            tell(player, unavailable);
            return false;
        }
        return true;
    }

    private void finishStartingRun(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
        World world, DeliveryStore.Run run, Ref<EntityStore> dispatcher) {
        conversations.remove(player.getUuid());
        Run currentRun = deliveries.profile(player.getUuid(), player.getUsername()).active();
        if (currentRun == null || !currentRun.identity().equals(run.identity())) {
            return;
        }
        if (!ref.isValid() || closing || departingPlayers.contains(player) || player.getReference() != ref
            || ref.getStore().getExternalData().getWorld() != world) {
            cancelRun(player, run.identity(), "You left the delivery world/instance."); return;
        }
        if ((dispatcher != null && !isDispatcherInReach(player, dispatcher)) || store.getComponent(ref, DeathComponent.getComponentType()) != null
            || store.getComponent(ref, CarriedBlock.getComponentType()) != null || !eligibleRecipients(world).contains(currentRun.recipient().id())
            || !DeliveryAssets.missing().isEmpty()) {
            cancelRun(player, run.identity(), "The parcel could not be equipped or its destination became unavailable. Choose again."); return;
        }
        DeliveryAssignment assignment = attachAssignment(player, currentRun, world);
        try {
            parcels.equip(store, ref, assignment);
            announceAssignment(player, currentRun);
        } catch (RuntimeException failed) {
            abandon(assignment, "The parcel could not be equipped. Choose again.");
            logFailure(failed);
        }
    }

    private String unavailableReason(World world) {
        List<String> missing = DeliveryAssets.missing();
        if (!missing.isEmpty()) {
            return "Deliveries unavailable: missing assets: " + String.join(", ", missing) + ". Contact an administrator.";
        }
        return eligibleRecipients(world).isEmpty() ? "No loaded, verified recipients are available in this world." : null;
    }

    public Set<String> eligibleRecipients(World world) {
        return recipients.eligible(world);
    }

    private <T> void operation(PlayerRef player, World world, Supplier<T> work, Consumer<T> done) {
        UUID id = player.getUuid();
        if (closing || departingPlayers.contains(player)) {
            return;
        }
        Object ticket = new Object();
        if (pending.putIfAbsent(id, ticket) != null) {
            tell(player, "Your previous parcel action is still being saved. Please wait.");
            return;
        }
        Ref<EntityStore> reference = player.getReference();
        actions.onWorld(() -> {
            synchronized (player) {
                if (closing || departingPlayers.contains(player) || pending.get(id) != ticket || player.getReference() != reference) {
                    throw new IllegalStateException("You left the delivery world/instance.");
                }
                return work.get();
            }
        }, world::execute, (result, error) -> {
            if (!pending.remove(id, ticket)) {
                return;
            }
            if (closing || departingPlayers.contains(player) || player.getReference() != reference) {
                return;
            }
            if (error != null) {
                logFailure(error);
                tell(player, errorMessage(error));
                return;
            }
            try { done.accept(result); }
            catch (RuntimeException failed) {
                logFailure(failed);
                tell(player, "The parcel action could not finish. Try again or use /parcel stop.");
            }
        }, stopped -> {
            if (!pending.remove(id, ticket)) {
                return;
            }
            Run run = deliveries.profile(id, player.getUsername()).active();
            if (run != null) {
                cancelRun(player, run.identity(), "You left the delivery world/instance.");
            }
        });
    }

    private static String errorMessage(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error instanceof IllegalStateException ? error.getMessage() : "Your progress could not be saved. Please try again.";
    }
    private static void logFailure(Throwable error) {
        HytaleLogger.forEnclosingClass().atWarning().withCause(error).log("Delivery action failed");
    }
    private static void tell(PlayerRef player, String text) {
        if (text != null) {
            player.sendMessage(DeliveryMessages.withPrefix(Message.raw(text)));
        }
    }

    private DeliveryAssignment attachAssignment(PlayerRef player, DeliveryStore.Run run, World world) {
        DeliveryAssignment assignment = new DeliveryAssignment(new DeliverySession(run.identity(), run.option()), player, run, world);
        activeAssignments.put(player.getUuid(), assignment);
        return assignment;
    }

    private void announceAssignment(PlayerRef player, DeliveryStore.Run run) {
        if (run.testMode()) {
            tell(player, "Test parcel started. This delivery awards no points. Use /parcel stop to end it.");
        }
        for (Message message : assignmentMessages(run)) {
            player.sendMessage(DeliveryMessages.withPrefix(message));
        }
    }

    private DeliveryStore.Destination currentRecipient(DeliveryStore.Destination recipient) {
        Destination latest = deliveries.recipient(recipient.id());
        return latest == null ? recipient : latest;
    }

    private List<Message> assignmentMessages(DeliveryStore.Run run) {
        Destination recipient = currentRecipient(run.recipient());
        return DeliveryMessages.assignment(run.option(), recipient.name(), recipient.hint());
    }

    private String destinationText(DeliveryStore.Destination recipient) {
        recipient = currentRecipient(recipient);
        return DeliveryMessages.destinationText(recipient.name(), recipient.hint());
    }

    /** Shows simple delivery instructions for testing. */
    public String deliver(PlayerRef player) {
        Run run = deliveries.profile(player.getUuid(), player.getUsername()).active();
        if (run == null) {
            return "No active parcel. Talk to the dispatcher to start a delivery.";
        }
        return destinationText(run.recipient()) + ". Press F near the recipient or throw the parcel at them.";
    }

    public String deliverTo(PlayerRef player, Ref<EntityStore> npc) {
        synchronized (player) {
            return deliverToFor(player, npc);
        }
    }

    private String deliverToFor(PlayerRef player, Ref<EntityStore> npc) {
        Ref<EntityStore> ref = player.getReference();
        if (departingPlayers.contains(player) || !nearNpc(ref, npc)) {
            return "Move closer to the recipient.";
        }
        Store<EntityStore> store = ref.getStore();
        DeliveryNpc recipient = store.getComponent(npc, npcType);
        if (recipient == null || !recipient.recipient() || !recipients.isRegistered(npc, recipient)) {
            return "This NPC is not an available parcel recipient.";
        }
        DeliveryAssignment assignment = activeAssignments.get(player.getUuid());
        if (assignment == null) {
            return null;
        }
        if (!correctDestination(assignment, recipient, store.getExternalData().getWorld())) {
            return "That present is for " + assignment.run.recipient().name() + ". " + destinationText(assignment.run.recipient());
        }
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            return "Respawn before delivering your parcel.";
        }
        if (!assignment.session.identity().equals(identityOf(store.getComponent(ref, CarriedBlock.getComponentType())))) {
            return "Pick up your parcel and carry it to the recipient first.";
        }
        completeDelivery(assignment, npc);
        return null;
    }

    /** Checks a thrown parcel hit and starts completion for the correct recipient. */
    public void deliverThrownTo(Ref<EntityStore> projectile, Ref<EntityStore> npc) {
        if (projectile == null || npc == null || !projectile.isValid() || !npc.isValid()
            || projectile.getStore() != npc.getStore()) {
            return;
        }
        Store<EntityStore> store = projectile.getStore();
        ThrownPresent parcel = store.getComponent(projectile, thrownPresentType);
        DeliveryNpc recipient = store.getComponent(npc, npcType);
        if (parcel == null || parcel.identity() == null || recipient == null || !recipient.recipient() || !recipients.isRegistered(npc, recipient)) {
            return;
        }
        DeliveryAssignment assignment = activeAssignments.get(parcel.identity().ownerId());
        if (assignment == null) {
            return;
        }
        PlayerRef player = assignment.player;
        synchronized (player) {
            Ref<EntityStore> ref = player.getReference();
            if (departingPlayers.contains(player) || ref == null || !ref.isValid() || ref.getStore() != store
                || store.getComponent(ref, DeathComponent.getComponentType()) != null
                || !assignment.parcelEntities.contains(projectile)
                || !assignment.session.ownsDrop(parcel.identity(), parcel.dropId())
                || !correctDestination(assignment, recipient, store.getExternalData().getWorld())) {
                return;
            }
            TransformComponent position = store.getComponent(projectile, TransformComponent.getComponentType());
            if (position == null || tooFar(store.getComponent(ref, TransformComponent.getComponentType()), position.getPosition())) {
                abandon(assignment, "You moved more than 64 blocks from your parcel.");
                return;
            }
            completeDelivery(assignment, npc);
        }
    }

    private void completeDelivery(DeliveryAssignment assignment, Ref<EntityStore> recipientNpc) {
        PlayerRef player = assignment.player;
        if (!assignment.session.isActive() || pending.containsKey(player.getUuid())) {
            return;
        }
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid() || ref.getStore().getExternalData().getWorld() != assignment.world) {
            return;
        }
        DeliveryAssignmentState state = ref.getStore().getComponent(ref, assignmentType);
        if (state == null || state.effectConflict || !DeliveryAssets.missing().isEmpty()) {
            abandon(assignment, "Your parcel effects are unavailable or conflicted. You may choose again.");
            return;
        }
        String recipientId = assignment.run.recipient().id();
        operation(player, assignment.world,
            () -> assignment.session.isActive()
            && deliveries.finishAtRecipient(player.getUuid(), player.getUsername(), assignment.session.identity().assignmentId(), recipientId),
            finished -> {
            if (!finished) {
                tell(player, "This delivery has ended. Talk to the dispatcher to start again.");
                return;
            }
            activeAssignments.remove(player.getUuid(), assignment);
            end(assignment);
            tell(player, "Parcel delivered! +" + (assignment.run.testMode() ? 0 : assignment.session.option().points()) + " points. Total: "
                + deliveries.profile(player.getUuid(), player.getUsername()).score() + ". Effects removed.");
            celebrateRecipient(recipientNpc);
        });
    }

    /** Plays the recipient's chosen celebration, or a random one if none is set. */
    private void celebrateRecipient(Ref<EntityStore> npc) {
        if (closing || npc == null || !npc.isValid()) {
            return;
        }
        Store<EntityStore> store = npc.getStore();
        DeliveryNpc marker = store.getComponent(npc, npcType);
        if (marker == null || !marker.recipient() || !recipients.isRegistered(npc, marker)
            || store.getComponent(npc, NetworkId.getComponentType()) == null) {
            return;
        }
        // Keep the saved score even if the celebration fails.
        try {
            Destination recipient = deliveries.recipient(marker.id());
            String tag = DeliveryCelebrationEmotes.choose(recipient.celebrationEmote(),
                CosmeticsModule.get().getRegistry().getEmotesInGame().keySet(), EmoteAsset.getAssetMap().getAssetMap().keySet(),
                ThreadLocalRandom.current());
            AnimationUtils.playAnimation(npc, AnimationSlot.Emote, null, tag, true, store);
        } catch (RuntimeException failed) {
            HytaleLogger.forEnclosingClass().atWarning().withCause(failed)
                .log("Could not play recipient celebration emote");
        }
    }

    public void stop(PlayerRef player) {
        DeliveryAssignment assignment = activeAssignments.get(player.getUuid());
        if (assignment != null) {
            abandon(assignment, "You stopped your delivery.");
        } else {
            Run run = deliveries.profile(player.getUuid(), player.getUsername()).active();
            if (run != null) {
                cancelRun(player, run.identity(), "You stopped your delivery.");
            } else {
                tell(player, "You have no active parcel. Your offered choices are unchanged.");
            }
        }
    }

    private void abandon(DeliveryAssignment assignment, String reason) {
        if (activeAssignments.remove(assignment.player.getUuid(), assignment)) {
            end(assignment);
            Profile profile = deliveries.profile(assignment.player.getUuid(), assignment.player.getUsername());
            if (profile.active() == null && profile.outcome() == Outcome.COMPLETED
                && assignment.session.identity().assignmentId().equals(profile.lastFinished())) {
                return;
            }
            cancelRun(assignment.player, assignment.session.identity(), reason);
        }
    }

    private void cancelRun(PlayerRef player, DeliveryIdentity identity, String reason) {
        if (deliveries.finish(player.getUuid(), player.getUsername(), identity.assignmentId(), Outcome.ABANDONED)
            && !departingPlayers.contains(player) && !closing) {
            tell(player, reason + " Delivery abandoned; no points awarded.");
        }
    }

    public void disconnect(PlayerRef player) {
        stopForDeparture(player);
    }

    public void leftWorld(PlayerRef player) {
        stopForDeparture(player);
    }

    private void stopForDeparture(PlayerRef player) {
        synchronized (player) {
            departingPlayers.add(player);
            conversations.remove(player.getUuid());
            pending.remove(player.getUuid());
            deliveries.leave(player.getUuid());
            DeliveryAssignment assignment = activeAssignments.remove(player.getUuid());
            if (assignment != null) {
                end(assignment);
            }
        }
    }

    public void shutdown() {
        closing = true;
        retries.shutdown();
        recipients.retryRetirements();
        conversations.clear();
        pending.clear();
        for (DeliveryAssignment assignment : List.copyOf(activeAssignments.values())) {
            if (activeAssignments.remove(assignment.session.identity().ownerId(), assignment)) {
                deliveries.leave(assignment.player.getUuid());
                end(assignment);
            }
        }
        deliveries.close();
        recipients.clear();
    }

    // Interactions and ECS systems use these entry points; physical parcel logic lives in DeliveryParcels.
    public DeliveryIdentity identityOf(CarriedBlock block) {
        return parcels.identityOf(block);
    }
    public DeliverySession session(DeliveryIdentity identity) {
        return parcels.session(identity);
    }
    public boolean isCurrentDrop(ThrownPresent parcel) {
        return parcels.isCurrentDrop(parcel);
    }
    public ThrownPresent beginThrow(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor) {
        return parcels.beginThrow(player, accessor);
    }
    public boolean track(Ref<EntityStore> entity, ThrownPresent parcel) {
        return parcels.track(entity, parcel);
    }
    public void untrack(Ref<EntityStore> entity, ThrownPresent parcel) {
        parcels.untrack(entity, parcel);
    }
    public boolean pickUp(Ref<EntityStore> player, Ref<EntityStore> target, CommandBuffer<EntityStore> buffer) {
        return parcels.pickUp(player, target, buffer);
    }
    public void tryNearbyPickup(Ref<EntityStore> target, ThrownPresent parcel, CommandBuffer<EntityStore> buffer) {
        parcels.tryNearbyPickup(target, parcel, buffer);
    }
    public void adoptNativeDrop(Ref<EntityStore> ref, CarriedBlock block, CommandBuffer<EntityStore> buffer) {
        parcels.adoptNativeDrop(ref, block, buffer);
    }
    public void removeStalePlayerState(Ref<EntityStore> ref, DeliveryAssignmentState state, CommandBuffer<EntityStore> buffer) {
        parcels.removeStalePlayerState(ref, state, buffer);
    }

    /** Checks delivery health from the player, even when the parcel's chunk is unloaded. */
    public void monitor(Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player) {
        if (closing || departingPlayers.contains(player) || !ref.isValid()) {
            return;
        }
        Profile profile = deliveries.profile(player.getUuid(), player.getUsername());
        if (profile.notice() != null && !pending.containsKey(player.getUuid())) {
            tell(player, deliveries.takeNotice(player.getUuid(), player.getUsername()));
        }
        DeliveryAssignment assignment = activeAssignments.get(player.getUuid());
        if (assignment == null) {
            parcels.clearStaleState(store, ref, player.getUuid());
            return;
        }
        if (store.getExternalData().getWorld() != assignment.world) {
            abandon(assignment, "You left the delivery world/instance.");
            return;
        }
        if (profile.active() == null) {
            activeAssignments.remove(player.getUuid(), assignment); end(assignment);
            return;
        }
        long now = System.nanoTime();
        String failure = parcelFailure(store, ref, assignment);
        if (failure == null) {
            failure = carriedParcelFailure(store, ref, assignment, now);
        }
        if (failure != null) {
            abandon(assignment, failure);
            return;
        }
        DeliveryAssignmentState state = store.getComponent(ref, assignmentType);
        if (state != null && state.effectConflict) {
            abandon(assignment, "Another effect or plugin prevented your parcel effects from working. You may choose again.");
        }
    }

    private String parcelFailure(Store<EntityStore> store, Ref<EntityStore> ref, DeliveryAssignment assignment) {
        if (!DeliveryAssets.missing().isEmpty()) {
            return "Required delivery assets became unavailable. Contact an administrator.";
        }
        Vector3d position = assignment.lastParcelPosition;
        if (position != null && position.y < ChunkUtil.MIN_ENTITY_Y) {
            return "Your parcel fell outside the world.";
        }
        if (!assignment.session.isCarried() && position != null
            && tooFar(store.getComponent(ref, TransformComponent.getComponentType()), position)) {
            return "You moved more than 64 blocks from your parcel.";
        }
        return null;
    }

    private String carriedParcelFailure(Store<EntityStore> store, Ref<EntityStore> ref, DeliveryAssignment assignment, long now) {
        boolean missing = assignment.session.isCarried() && !pending.containsKey(assignment.player.getUuid())
            && store.getComponent(ref, DeathComponent.getComponentType()) == null
            && !assignment.session.identity().equals(identityOf(store.getComponent(ref, CarriedBlock.getComponentType())));
        if (!missing) {
            assignment.missingCarriedSince = 0;
        } else if (assignment.missingCarriedSince == 0) {
            assignment.missingCarriedSince = now;
        } else if (now - assignment.missingCarriedSince > MISSING_PARCEL_GRACE_NANOS) {
            return "Your carried parcel was removed.";
        }
        return null;
    }

    private boolean correctDestination(DeliveryAssignment assignment, DeliveryNpc recipient, World world) {
        Run run = deliveries.profile(assignment.player.getUuid(), assignment.player.getUsername()).active();
        if (run == null || !run.identity().equals(assignment.session.identity())) {
            return false;
        }
        return run.recipient().id().equals(recipient.id()) && assignment.world == world
            && (run.recipient().worldId().equals(world.getWorldConfig().getUuid()));
    }

    public void recipientRemoved(Ref<EntityStore> ref, DeliveryNpc marker, UUID entityId, RemoveReason reason) {
        recipients.forget(ref, marker);
        if (!closing) {
            recipients.removed(marker, entityId, reason);
        }
    }

    private static boolean tooFar(TransformComponent player, Vector3dc parcel) {
        return player != null && DeliverySeparationRules.tooFar(player.getPosition(), parcel);
    }

    public void observeRecipient(Ref<EntityStore> ref, DeliveryNpc marker) {
        recipients.observe(ref, marker);
    }

    public void parcelRemoved(ThrownPresent parcel, RemoveReason reason) {
        if (parcel == null || parcel.identity() == null) {
            return;
        }
        DeliveryAssignment assignment = activeAssignments.get(parcel.identity().ownerId());
        if (assignment == null || !assignment.session.ownsDrop(parcel.identity(), parcel.dropId())) {
            return;
        }
        if (reason != RemoveReason.UNLOAD && !closing) {
            // An unchanged drop ID means the parcel was removed without being picked up.
            assignment.world.execute(() -> {
                if (assignment.session.ownsDrop(parcel.identity(), parcel.dropId())) {
                    abandon(assignment, "Your parcel was destroyed.");
                }
            });
        }
    }

    private void end(DeliveryAssignment assignment) {
        // End the session before cleanup so parcel removal cannot abandon it again.
        assignment.session.finish();
        parcels.cleanup(assignment);
    }

}
