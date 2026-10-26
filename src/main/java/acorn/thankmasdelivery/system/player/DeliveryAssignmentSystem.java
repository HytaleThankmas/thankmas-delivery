package acorn.thankmasdelivery.system.player;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.effect.DeliveryEffectLifecycleSystem;
import acorn.thankmasdelivery.model.DeliveryIdentity;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DeliveryAssignmentSystem extends EntityTickingSystem<EntityStore> {
    private final DeliveryService deliveryService;
    private final ComponentType<EntityStore, DeliveryAssignmentState> assignmentType;
    private final Query<EntityStore> query;

    public DeliveryAssignmentSystem(
        DeliveryService deliveryService,
        ComponentType<EntityStore, DeliveryAssignmentState> assignmentType
    ) {
        this.deliveryService = deliveryService;
        this.assignmentType = assignmentType;
        this.query = PlayerRef.getComponentType();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(
        float deltaSeconds,
        int index,
        ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        DeliveryAssignmentState assignmentState = chunk.getComponent(index, assignmentType);
        PlayerRef player = chunk.getComponent(index, PlayerRef.getComponentType());
        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        if (player == null) {
            return;
        }
        if (assignmentState == null && !deliveryService.hasActiveDelivery(player.getUuid())) {
            return;
        }
        commandBuffer.run(currentStore -> deliveryService.monitor(currentStore, ref, player));
        if (assignmentState == null) {
            return;
        }
        DeliveryIdentity identity = assignmentState.identity();
        if (identity == null || !player.getUuid().equals(identity.ownerId()) || deliveryService.session(identity) == null) {
            deliveryService.removeStalePlayerState(ref, assignmentState, commandBuffer);
        } else if (commandBuffer.getComponent(ref, DeathComponent.getComponentType()) == null) {
            DeliveryEffectLifecycleSystem.ensureEffects(ref, assignmentState, commandBuffer);
        }
    }
}
