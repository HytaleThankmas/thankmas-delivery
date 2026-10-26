package acorn.thankmasdelivery.system.parcel;

import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Links parcels dropped by Hytale's normal carrying system to their deliveries. */
public final class DeliveryNativeDropSystem extends RefSystem<EntityStore> {
    private final DeliveryService deliveryService;

    public DeliveryNativeDropSystem(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(CarriedBlock.getComponentType(), Query.not(PlayerRef.getComponentType()));
    }

    @Override
    public void onEntityAdded(
        Ref<EntityStore> ref,
        AddReason reason,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        deliveryService.adoptNativeDrop(ref, store.getComponent(ref, CarriedBlock.getComponentType()), commandBuffer);
    }

    @Override
    public void onEntityRemove(
        Ref<EntityStore> ref,
        RemoveReason reason,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
    }
}
