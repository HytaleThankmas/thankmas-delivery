package acorn.thankmasdelivery.npc;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Chooses a recipient's celebration from the random list or an emote set by an admin. */
public final class DeliveryCelebrationEmotes {
    public static final List<String> RANDOM_EMOTES = List.of("BlowKiss", "Salute", "DanceBoogie", "DancePop");
    private DeliveryCelebrationEmotes() { }

    public static String resolve(String input, Set<String> builtins, Set<String> serverEmotes) {
        String tag = input.strip();
        if (tag.length() >= 2 && ((tag.startsWith("\"") && tag.endsWith("\"")) || (tag.startsWith("'") && tag.endsWith("'")))) {
            tag = tag.substring(1, tag.length() - 1).strip();
        }
        if (tag.isEmpty() || tag.equalsIgnoreCase("random")) {
            return "";
        }
        if (builtins.contains(tag) || serverEmotes.contains(tag)) {
            return tag;
        }
        String normalized = normalize(tag);
        for (String candidate : builtins) {
            if (normalize(candidate).equals(normalized)) {
                return candidate;
            }
        }
        for (String candidate : serverEmotes) {
            if (normalize(candidate).equals(normalized)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown emote tag: " + tag + ". Use a registered player/server emote, or random to clear the override.");
    }

    public static String choose(String override, Set<String> builtins, Set<String> serverEmotes, RandomGenerator random) {
        if (override != null && !override.isBlank()) {
            return resolve(override, builtins, serverEmotes);
        }
        List<String> available = RANDOM_EMOTES.stream().filter(tag -> builtins.contains(tag) || serverEmotes.contains(tag)).toList();
        if (available.isEmpty()) {
            throw new IllegalStateException("None of the recipient's random celebration emotes are available.");
        }
        return available.get(random.nextInt(available.size()));
    }

    private static String normalize(String tag) {
        return tag.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }
}
