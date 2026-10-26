package acorn.thankmasdelivery.system.npc;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Ends deliveries for a deleted recipient; chunk unloading is ignored. */
public final class DeliveryNpcRemovalSystem extends RefSystem<EntityStore> {
    private final DeliveryService service;
    private final ComponentType<EntityStore, DeliveryNpc> type;
    public DeliveryNpcRemovalSystem(DeliveryService service, ComponentType<EntityStore, DeliveryNpc> type) {
        this.service = service;
        this.type = type;
    }
    @Override
    public Query<EntityStore> getQuery() {
        return type;
    }
    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        service.observeRecipient(ref, store.getComponent(ref, type));
    }
    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        service.recipientRemoved(ref, store.getComponent(ref, type), uuid == null ? null : uuid.getUuid(), reason);
    }
}
