package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.model.DeliveryOption;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

public final class DeliveryEffectRules {
    private static final Map<DeliveryEffect, EnumSet<DeliveryEffect>> INCOMPATIBLE_EFFECTS =
    new EnumMap<>(DeliveryEffect.class);

    static {
        for (DeliveryEffect effect : DeliveryEffect.values()) {
            INCOMPATIBLE_EFFECTS.put(effect, EnumSet.noneOf(DeliveryEffect.class));
        }

        // A parcel cannot have both effects in any of these pairs.
        registerIncompatiblePair(DeliveryEffect.SPEED_INCREASE, DeliveryEffect.DASH_ONLY);
        registerIncompatiblePair(DeliveryEffect.SPEED_INCREASE, DeliveryEffect.HEAVY);
        registerIncompatiblePair(DeliveryEffect.JUMP_HEIGHT_INCREASE, DeliveryEffect.HEAVY);
        registerIncompatiblePair(DeliveryEffect.CONSTANT_BOUNCE, DeliveryEffect.HEAVY);
        registerIncompatiblePair(DeliveryEffect.BIG, DeliveryEffect.SMALL);
        registerIncompatiblePair(DeliveryEffect.GOBLIN, DeliveryEffect.KWEEBEC);
    }

    private DeliveryEffectRules() {
    }

    private static boolean areAllCompatible(@Nonnull Set<DeliveryEffect> effects) {
        for (DeliveryEffect effect : effects) {
            if (!Collections.disjoint(effects, INCOMPATIBLE_EFFECTS.get(effect))) {
                return false;
            }
        }
        return true;
    }

    /** Big or Small must be paired with Goblin or Kweebec, with no other effects. */
    public static boolean canOffer(@Nonnull Set<DeliveryEffect> effects) {
        if (!areAllCompatible(effects)) {
            return false;
        }
        if (effects.contains(DeliveryEffect.BIG) || effects.contains(DeliveryEffect.SMALL)) {
            return effects.size() == 2 && (effects.contains(DeliveryEffect.GOBLIN) || effects.contains(DeliveryEffect.KWEEBEC));
        }
        return true;
    }

    /** Reads effect names separated by commas from the admin test command. */
    public static DeliveryOption parseSelection(String text) {
        EnumSet<DeliveryEffect> effects = EnumSet.noneOf(DeliveryEffect.class);
        for (String part : unquote(text).split(",", -1)) {
            String name = normalize(part);
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Separate effect names with commas; do not leave an empty entry.");
            }
            DeliveryEffect selected = switch (name) {
                case "speed" -> DeliveryEffect.SPEED_INCREASE;
                case "jump", "jumpboost" -> DeliveryEffect.JUMP_HEIGHT_INCREASE;
                case "dash" -> DeliveryEffect.DASH_ONLY;
                case "bounce" -> DeliveryEffect.CONSTANT_BOUNCE;
                case "goblin" -> DeliveryEffect.GOBLIN;
                default -> null;
            };
            if (selected == null) {
                for (DeliveryEffect effect : DeliveryEffect.values()) {
                    if (name.equals(normalize(effect.displayName())) || name.equals(normalize(effect.name()))) {
                        selected = effect; break;
                    }
                }
            }
            if (selected == null) {
                throw new IllegalArgumentException("Unknown effect: " + part.strip() + ". Use /parcel effectlist for names.");
            }
            if (!effects.add(selected)) {
                throw new IllegalArgumentException("Duplicate effect: " + selected.displayName() + ".");
            }
        }
        if (effects.size() > 4) {
            throw new IllegalArgumentException("Choose between 1 and 4 distinct effects.");
        }
        for (DeliveryEffect effect : effects) {
            for (DeliveryEffect other : effects) {
                if (INCOMPATIBLE_EFFECTS.get(effect).contains(other)) {
                    throw new IllegalArgumentException(effect.displayName() + " and " + other.displayName() + " are incompatible.");
                }
            }
        }
        if (!canOffer(effects)) {
            throw new IllegalArgumentException("Big or Small requires Goblin or Kweebec, with no extra effects.");
        }
        return new DeliveryOption(effects);
    }

    private static String normalize(String name) {
        return unquote(name).toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
    }

    private static String unquote(String text) {
        text = text.strip();
        if (text.length() >= 2 && text.indexOf(text.charAt(0), 1) == text.length() - 1 && ((text.startsWith("\"") && text.endsWith("\""))
            || (text.startsWith("'") && text.endsWith("'")))) {
            return text.substring(1, text.length() - 1).strip();
        }
        return text;
    }

    private static void registerIncompatiblePair(DeliveryEffect first, DeliveryEffect second) {
        INCOMPATIBLE_EFFECTS.get(first).add(second);
        INCOMPATIBLE_EFFECTS.get(second).add(first);
    }
}
