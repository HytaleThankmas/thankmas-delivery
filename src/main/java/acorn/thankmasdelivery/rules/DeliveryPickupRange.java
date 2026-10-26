package acorn.thankmasdelivery.rules;

import org.joml.Vector3dc;

public final class DeliveryPickupRange {
    private static final double HORIZONTAL_RADIUS = 2.0;
    private static final double VERTICAL_REACH = 3.5;

    private DeliveryPickupRange() {
    }

    public static boolean contains(Vector3dc player, Vector3dc parcel) {
        double dx = player.x() - parcel.x();
        double dz = player.z() - parcel.z();
        return dx * dx + dz * dz <= HORIZONTAL_RADIUS * HORIZONTAL_RADIUS
            && Math.abs(player.y() - parcel.y()) <= VERTICAL_REACH;
    }
}
