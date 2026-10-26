package acorn.thankmasdelivery.component;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Remembers whether the player was jumping or on the ground, so each boost or bounce happens once. */
public final class DeliveryMovementState implements Component<EntityStore> {
    private boolean jumping;
    private boolean onGround;

    public boolean startedJumping(boolean jumpingNow) {
        boolean started = jumpingNow && !jumping;
        jumping = jumpingNow;
        return started;
    }

    public boolean landed(boolean onGroundNow) {
        boolean landed = onGroundNow && !onGround;
        onGround = onGroundNow;
        return landed;
    }

    public void reset() {
        jumping = false;
        onGround = false;
    }

    @Override
    public DeliveryMovementState clone() {
        DeliveryMovementState copy = new DeliveryMovementState();
        copy.jumping = jumping;
        copy.onGround = onGround;
        return copy;
    }
}
