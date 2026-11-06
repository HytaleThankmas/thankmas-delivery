package acorn.thankmasdelivery.system.player;

import acorn.thankmasdelivery.component.DeliveryMovementState;
import acorn.thankmasdelivery.component.DeliveryPointsComponent;
import acorn.thankmasdelivery.persistence.DeliveryStore;

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
    private final ComponentType<EntityStore, DeliveryPointsComponent> pointsType;
    private final DeliveryStore deliveryStore;

    public DeliveryPlayerInitSystem(
        @Nonnull ComponentType<EntityStore, DeliveryMovementState> movementStateType,
        @Nonnull ComponentType<EntityStore, DeliveryPointsComponent> pointsType,
        DeliveryStore deliveryStore
    ) {
        this.movementStateType = movementStateType;
        this.pointsType = pointsType;
        this.deliveryStore = deliveryStore;
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
        holder.ensureComponent(pointsType);
        DeliveryPointsComponent pointsComp = holder.getComponent(pointsType);
        if (pointsComp != null && pointsComp.getPoints() == 0 && deliveryStore != null) {
            PlayerRef playerRef = holder.getComponent(PlayerRef.getComponentType());
            if (playerRef != null) {
                long legacyScore = deliveryStore.profile(playerRef.getUuid(), playerRef.getUsername()).score();
                if (legacyScore > 0) {
                    pointsComp.setPoints((int) legacyScore);
                }
            }
        }
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
