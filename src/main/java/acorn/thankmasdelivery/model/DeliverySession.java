package acorn.thankmasdelivery.model;

import java.util.UUID;

/** Tracks whether a delivery is active, whether its parcel is being carried, and when it can be picked up. */
public final class DeliverySession {
    public static final long PICKUP_DELAY_TIME = 1_500_000_000L;
    private final DeliveryIdentity identity;
    private final DeliveryOption option;
    private boolean active = true;
    private UUID dropId;
    private long droppedAtTime;

    public DeliverySession(DeliveryIdentity identity, DeliveryOption option) {
        this.identity = identity;
        this.option = option;
    }

    public DeliveryIdentity identity() {
        return identity;
    }

    public DeliveryOption option() {
        return option;
    }

    public synchronized boolean isActive() {
        return active;
    }

    public synchronized boolean isCarried() {
        return active && dropId == null;
    }

    public synchronized boolean matches(DeliveryIdentity candidate) {
        return active && identity.equals(candidate);
    }

    public synchronized UUID drop(long nowTime) {
        if (!isCarried()) {
            return null;
        }
        dropId = UUID.randomUUID();
        droppedAtTime = nowTime;
        return dropId;
    }

    public synchronized boolean ownsDrop(DeliveryIdentity candidate, UUID token) {
        return matches(candidate) && dropId != null && dropId.equals(token);
    }

    public synchronized boolean pickUp(UUID playerId, DeliveryIdentity candidate, UUID token, long nowTime) {
        if (!identity.ownerId().equals(playerId) || !ownsDrop(candidate, token)
            || nowTime - droppedAtTime < PICKUP_DELAY_TIME) {
            return false;
        }
        dropId = null;
        return true;
    }

    public synchronized void cancelDrop(UUID token) {
        if (active && dropId != null && dropId.equals(token)) {
            dropId = null;
        }
    }

    public synchronized void finish() {
        active = false;
        dropId = null;
    }
}
