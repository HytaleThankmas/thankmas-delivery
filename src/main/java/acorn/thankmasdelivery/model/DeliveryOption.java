package acorn.thankmasdelivery.model;

import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.effect.DeliveryEffectRules;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

public record DeliveryOption(Set<DeliveryEffect> effects) {
    public static final int CHOICE_COUNT = 3;
    private static final int MAX_EFFECT_COUNT = 4;
    private static final int[] MIN_POINTS = {1, 4, 6};
    private static final int[] MAX_POINTS = {3, 6, 9};
    private static final List<List<DeliveryOption>> CHOICES_BY_SLOT = buildChoicesBySlot();

    public DeliveryOption {
        if (effects.isEmpty() || effects.size() > MAX_EFFECT_COUNT || !DeliveryEffectRules.canOffer(effects)) {
            throw new IllegalArgumentException("A parcel needs 1-4 distinct compatible effects.");
        }
        // Enum order applies a transformation before Big/Small. Callers cannot change the set.
        effects = java.util.Collections.unmodifiableSet(EnumSet.copyOf(effects));
    }

    public int points() {
        return effects.stream().mapToInt(effect -> switch (effect.category()) {
            case POSITIVE -> 1;
            case NEUTRAL -> 2;
            case NEGATIVE -> 3;
        }).sum();
    }

    /** Checks the point range for offer slot 0, 1, or 2. */
    private boolean fitsChoice(int choiceIndex) {
        int points = points();
        return choiceIndex >= 0 && choiceIndex < CHOICE_COUNT
            && points >= MIN_POINTS[choiceIndex] && points <= MAX_POINTS[choiceIndex];
    }

    public static List<DeliveryOption> randomChoices(RandomGenerator random) {
        List<DeliveryOption> choices = new ArrayList<>(CHOICE_COUNT);
        for (List<DeliveryOption> candidates : CHOICES_BY_SLOT) {
            int excluded = 0;
            for (DeliveryOption previous : choices) {
                if (candidates.contains(previous)) {
                    excluded++;
                }
            }
            int selected = random.nextInt(candidates.size() - excluded);
            // Skip previous choices so overlapping point ranges cannot produce the same parcel twice.
            for (DeliveryOption candidate : candidates) {
                if (!choices.contains(candidate) && selected-- == 0) {
                    choices.add(candidate);
                    break;
                }
            }
        }
        return List.copyOf(choices);
    }

    private static List<List<DeliveryOption>> buildChoicesBySlot() {
        List<DeliveryOption> options = buildCompatibleOptions();
        List<List<DeliveryOption>> slots = new ArrayList<List<DeliveryOption>>(CHOICE_COUNT);
        for (int index = 0; index < CHOICE_COUNT; index++) {
            final int slot = index;
            slots.add(options.stream().filter(option -> option.fitsChoice(slot)).toList());
        }
        return List.copyOf(slots);
    }

    private static List<DeliveryOption> buildCompatibleOptions() {
        List<DeliveryOption> available = new ArrayList<>();
        DeliveryEffect[] allEffects = DeliveryEffect.values();
        for (int effectMask = 1; effectMask < (1 << allEffects.length); effectMask++) {
            if (Integer.bitCount(effectMask) > MAX_EFFECT_COUNT) {
                continue;
            }
            EnumSet<DeliveryEffect> effects = EnumSet.noneOf(DeliveryEffect.class);
            for (int effectIndex = 0; effectIndex < allEffects.length; effectIndex++) {
                if ((effectMask & (1 << effectIndex)) != 0) {
                    effects.add(allEffects[effectIndex]);
                }
            }
            if (DeliveryEffectRules.canOffer(effects)) {
                available.add(new DeliveryOption(effects));
            }
        }
        return List.copyOf(available);
    }
}
