package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.model.DeliveryIdentity;

import javax.annotation.Nullable;

public enum DeliveryEffect {
    SPEED_INCREASE(
        "Speed Increase",
        "Increases speed by 50%.",
        DeliveryEffectCategory.POSITIVE,
        "Parcel_Effect_Speed"
    ),
    JUMP_HEIGHT_INCREASE(
        "Jump Height Increase",
        "Increased jump height by 50%.",
        DeliveryEffectCategory.POSITIVE,
        null
    ),
    DASH_ONLY(
        "Dash Only",
        "Restricts movement, adds dash ability",
        DeliveryEffectCategory.NEUTRAL,
        "Parcel_Effect_Dash"
    ),
    BLINDNESS(
        "Blindness",
        "Prevents players from seeing 6 blocks in front of them",
        DeliveryEffectCategory.NEGATIVE,
        null
    ),
    CONSTANT_BOUNCE(
        "Constant Bounce",
        "Automatically bounces the carrier about two blocks high every time they land.",
        DeliveryEffectCategory.NEUTRAL,
        null
    ),
    HEAVY(
        "Heavy",
        "Reduces movement speed by 40%, prevents sprinting and jumping.",
        DeliveryEffectCategory.NEGATIVE,
        "Parcel_Effect_Heavy"
    ),
    GOBLIN(
        "Goblin",
        "Transforms you into a random Goblin, Goblin Boss, or Goblin Burner for this delivery.",
        DeliveryEffectCategory.NEUTRAL,
        null
    ),
    KWEEBEC(
        "Kweebec",
        "Transforms you into a Christmas Kweebec with a random green, blue, or pink outfit for this delivery.",
        DeliveryEffectCategory.NEUTRAL,
        null
    ),
    BIG(
        "Big",
        "Makes your Goblin or Kweebec form 50% larger.",
        DeliveryEffectCategory.NEGATIVE,
        null
    ),
    SMALL(
        "Small",
        "Makes your Goblin or Kweebec form half its normal size.",
        DeliveryEffectCategory.NEUTRAL,
        null
    ),
    STIFF_NECK(
        "Stiff Neck",
        "Reduces camera sensitivity by 90%",
        DeliveryEffectCategory.NEGATIVE,
        "Parcel_Effect_Stiff_Neck"
    );

    private final String displayName;
    private final String description;
    private final DeliveryEffectCategory category;
    @Nullable
    private final String entityEffectId;

    DeliveryEffect(
        String displayName,
        String description,
        DeliveryEffectCategory category,
        @Nullable String entityEffectId
    ) {
        this.displayName = displayName;
        this.description = description;
        this.category = category;
        this.entityEffectId = entityEffectId;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public DeliveryEffectCategory category() {
        return category;
    }

    public boolean isTransformation() {
        return this == GOBLIN || this == KWEEBEC;
    }

    @Nullable
    public String entityEffectId(DeliveryIdentity identity) {
        return switch (this) {
            case GOBLIN, KWEEBEC -> DeliveryForms.effectId(this, identity);
            default -> entityEffectId;
        };
    }

    @Nullable
    public String entityEffectId() {
        return entityEffectId;
    }
}
