package acorn.thankmasdelivery.system.player;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.component.DeliveryMovementState;
import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.rules.DeliveryMovementRules;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Set;

/** Applies parcel jump boosts and landing bounces after Hytale processes the player's movement input. */
public final class DeliveryMovementSystem extends EntityTickingSystem<EntityStore> {
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemDependency<>(Order.AFTER, PlayerSystems.ProcessPlayerInput.class, OrderPriority.CLOSEST)
    );

    private final ComponentType<EntityStore, DeliveryAssignmentState> assignmentType;
    private final ComponentType<EntityStore, DeliveryMovementState> movementStateType;
    private final Query<EntityStore> query;

    public DeliveryMovementSystem(
        ComponentType<EntityStore, DeliveryAssignmentState> assignmentType,
        ComponentType<EntityStore, DeliveryMovementState> movementStateType
    ) {
        this.assignmentType = assignmentType;
        this.movementStateType = movementStateType;
        query = Query.and(PlayerRef.getComponentType(), MovementStatesComponent.getComponentType(),
            Velocity.getComponentType(), assignmentType, movementStateType);
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float deltaSeconds, int index, ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        DeliveryMovementState state = chunk.getComponent(index, movementStateType);
        DeliveryAssignmentState assignment = chunk.getComponent(index, assignmentType);
        MovementStatesComponent movement = chunk.getComponent(index, MovementStatesComponent.getComponentType());
        Velocity velocity = chunk.getComponent(index, Velocity.getComponentType());
        if (state == null || assignment == null || movement == null || velocity == null) {
            return;
        }
        if (commandBuffer.getComponent(chunk.getReferenceTo(index), DeathComponent.getComponentType()) != null) {
            state.reset();
            return;
        }

        MovementStates flags = movement.getMovementStates();
        boolean startedJump = state.startedJumping(flags.jumping);
        boolean landed = state.landed(flags.onGround);
        MovementManager manager = commandBuffer.getComponent(chunk.getReferenceTo(index), MovementManager.getComponentType());
        MovementSettings settings = manager == null ? null : manager.getSettings();
        double jumpForce = settings == null ? 0 : (flags.swimming ? settings.swimJumpForce : settings.jumpForce);
        double impulse = DeliveryMovementRules.verticalImpulse(startedJump, landed,
            assignment.has(DeliveryEffect.JUMP_HEIGHT_INCREASE), assignment.has(DeliveryEffect.CONSTANT_BOUNCE), jumpForce);
        if (impulse != 0) {
            velocity.addInstruction(new Vector3d(0, impulse, 0), null, ChangeVelocityType.Add);
        }
    }
}
