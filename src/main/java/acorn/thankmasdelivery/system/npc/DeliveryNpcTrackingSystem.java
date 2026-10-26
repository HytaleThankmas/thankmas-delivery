package acorn.thankmasdelivery.system.npc;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Tracks loaded recipient entities so new deliveries use destinations that are available. */
public final class DeliveryNpcTrackingSystem extends DelayedEntitySystem<EntityStore> {
    private final DeliveryService service;
    private final ComponentType<EntityStore, DeliveryNpc> type;
    public DeliveryNpcTrackingSystem(DeliveryService service, ComponentType<EntityStore, DeliveryNpc> type) {
        // Names and hints are read when needed; moving NPC coordinates need only a periodic save.
        super(1.0f);
        this.service = service;
        this.type = type;
    }
    @Override
    public Query<EntityStore> getQuery() {
        return type;
    }
    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        service.observeRecipient(chunk.getReferenceTo(index), chunk.getComponent(index, type));
    }
}
