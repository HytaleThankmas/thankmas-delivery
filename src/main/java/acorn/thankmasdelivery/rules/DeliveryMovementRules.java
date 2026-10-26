package acorn.thankmasdelivery.rules;

/** Calculates how much upward speed to add for a jump boost or landing bounce. */
public final class DeliveryMovementRules {
    private static final double JUMP_SPEED_MULTIPLIER = Math.sqrt(1.5);
    private static final double BOUNCE_VELOCITY = 11.8;

    private DeliveryMovementRules() {
    }

    public static double verticalImpulse(boolean startedJump, boolean landed, boolean jumpBoost, boolean bounce, double jumpForce) {
        // If a jump and landing happen in the same update, apply the bounce once without adding a jump boost.
        if (landed && bounce) {
            return BOUNCE_VELOCITY;
        }
        if (!startedJump || !jumpBoost || !Double.isFinite(jumpForce) || jumpForce <= 0) {
            return 0;
        }
        return jumpForce * (JUMP_SPEED_MULTIPLIER - 1);
    }
}
