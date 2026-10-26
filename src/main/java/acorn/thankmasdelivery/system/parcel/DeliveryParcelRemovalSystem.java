package acorn.thankmasdelivery.system.parcel;

import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Reports parcel removal so the game can distinguish destruction, pickup, and chunk unloading. */
public final class DeliveryParcelRemovalSystem extends RefSystem<EntityStore> {
    private final DeliveryService service;
    private final ComponentType<EntityStore, ThrownPresent> type;
    public DeliveryParcelRemovalSystem(DeliveryService service, ComponentType<EntityStore, ThrownPresent> type) {
        this.service = service;
        this.type = type;
    }
    @Override
    public Query<EntityStore> getQuery() {
        return type;
    }
    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) { }
    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        service.parcelRemoved(store.getComponent(ref, type), reason);
    }
}
