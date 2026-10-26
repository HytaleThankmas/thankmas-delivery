package acorn.thankmasdelivery.system.npc;

import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.event.events.ecs.UseEntityEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DeliveryNpcUseSystem extends EntityEventSystem<EntityStore, UseEntityEvent.Pre> {
    private final DeliveryService service;

    public DeliveryNpcUseSystem(DeliveryService service) {
        super(UseEntityEvent.Pre.class);
        this.service = service;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
        CommandBuffer<EntityStore> buffer, UseEntityEvent.Pre event) {
        if (!event.isCancelled() && event.getInteractionType() == InteractionType.Use
            && service.queueNpcInteraction(chunk.getReferenceTo(index), event.getTargetEntity())) {
            event.setCancelled(true);
        }
    }
}
