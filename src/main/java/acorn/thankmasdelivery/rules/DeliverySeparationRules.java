package acorn.thankmasdelivery.rules;

import org.joml.Vector3dc;

public final class DeliverySeparationRules {
    public static final double MAX_DISTANCE = 64;
    private DeliverySeparationRules() { }
    public static boolean tooFar(Vector3dc player, Vector3dc parcel) {
        return !Double.isFinite(player.x()) || !Double.isFinite(player.y()) || !Double.isFinite(player.z())
            || !Double.isFinite(parcel.x()) || !Double.isFinite(parcel.y()) || !Double.isFinite(parcel.z())
            || player.distanceSquared(parcel) > MAX_DISTANCE * MAX_DISTANCE;
    }
}
