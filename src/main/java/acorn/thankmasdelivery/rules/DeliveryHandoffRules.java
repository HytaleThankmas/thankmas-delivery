package acorn.thankmasdelivery.rules;

public final class DeliveryHandoffRules {
    private DeliveryHandoffRules() { }
    public static boolean inReach(double distanceSquared) {
        return Double.isFinite(distanceSquared) && distanceSquared >= 0 && distanceSquared <= 36;
    }
}
