package acorn.thankmasdelivery.effect;

public enum DeliveryEffectCategory {
    POSITIVE("Positive"),
    NEUTRAL("Neutral"),
    NEGATIVE("Negative");

    private final String displayName;

    DeliveryEffectCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
