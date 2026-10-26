package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.model.DeliveryIdentity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Lists supported transformation models and chooses a stable variant for each delivery. */
public final class DeliveryForms {
    public static final List<String> MODEL_IDS = List.of(
        "Goblin", "Goblin_Boss", "Goblin_Burner",
        "Kweebec_Sapling_Christmas_Green", "Kweebec_Sapling_Christmas_Blue", "Kweebec_Sapling_Christmas_Pink"
    );

    private DeliveryForms() { }

    public static String effectId(DeliveryEffect effect, DeliveryIdentity identity) {
        int offset = switch (effect) {
            case GOBLIN -> 0;
            case KWEEBEC -> 3;
            default -> throw new IllegalArgumentException("This effect has no transformation model: " + effect);
        };
        UUID assignment = identity.assignmentId();
        long seed = assignment.getMostSignificantBits() ^ assignment.getLeastSignificantBits();
        int variant = new Random(seed).nextInt(3);
        return "Parcel_Effect_" + MODEL_IDS.get(offset + variant);
    }
}
