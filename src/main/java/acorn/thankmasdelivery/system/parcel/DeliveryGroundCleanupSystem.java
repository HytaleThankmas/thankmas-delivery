package acorn.thankmasdelivery.system.parcel;

import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Picks up nearby parcels for their owners and removes parcels from deliveries that have ended. */
public final class DeliveryGroundCleanupSystem extends EntityTickingSystem<EntityStore> {
    private final DeliveryService deliveryService;
    private final ComponentType<EntityStore, ThrownPresent> thrownPresentType;

    public DeliveryGroundCleanupSystem(
        DeliveryService deliveryService,
        ComponentType<EntityStore, ThrownPresent> thrownPresentType
    ) {
        this.deliveryService = deliveryService;
        this.thrownPresentType = thrownPresentType;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return thrownPresentType;
    }

    @Override
    public void tick(
        float deltaSeconds,
        int index,
        ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        ThrownPresent parcel = chunk.getComponent(index, thrownPresentType);
        if (parcel != null && !deliveryService.isCurrentDrop(parcel)) {
            Ref<EntityStore> ref = chunk.getReferenceTo(index);
            deliveryService.untrack(ref, parcel);
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        } else if (parcel != null) {
            deliveryService.tryNearbyPickup(chunk.getReferenceTo(index), parcel, commandBuffer);
        }
    }
}
