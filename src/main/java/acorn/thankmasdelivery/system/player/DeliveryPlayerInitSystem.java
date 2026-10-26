package acorn.thankmasdelivery.system.player;

import acorn.thankmasdelivery.component.DeliveryMovementState;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/** Adds the data used to track a player's jumps and landings. */
public final class DeliveryPlayerInitSystem extends HolderSystem<EntityStore> {
    private final ComponentType<EntityStore, DeliveryMovementState> movementStateType;

    public DeliveryPlayerInitSystem(
        @Nonnull ComponentType<EntityStore, DeliveryMovementState> movementStateType
    ) {
        this.movementStateType = movementStateType;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void onEntityAdd(
        @Nonnull Holder<EntityStore> holder,
        @Nonnull AddReason reason,
        @Nonnull Store<EntityStore> store
    ) {
        holder.ensureComponent(movementStateType);
    }

    @Override
    public void onEntityRemoved(
        @Nonnull Holder<EntityStore> holder,
        @Nonnull RemoveReason reason,
        @Nonnull Store<EntityStore> store
    ) {
        // Hytale removes this tracking data when the player entity is removed.
    }
}
