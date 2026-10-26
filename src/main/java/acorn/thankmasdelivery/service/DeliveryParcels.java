package acorn.thankmasdelivery.service;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.component.DeliveryOwnership;
import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.model.DeliveryIdentity;
import acorn.thankmasdelivery.model.DeliverySession;
import acorn.thankmasdelivery.rules.DeliveryPickupRange;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Predicate;

final class DeliveryParcels {
    private static final String PRESENT_BLOCK_ID = DeliveryService.PRESENT_BLOCK_ID;
    private static final double MAX_PICKUP_DISTANCE_SQUARED = 6 * 6;
    private final ComponentType<EntityStore, DeliveryAssignmentState> assignmentType;
    private final ComponentType<EntityStore, ThrownPresent> thrownPresentType;
    private final ComponentType<ChunkStore, DeliveryOwnership> ownershipType;
    private final ConcurrentMap<UUID, DeliveryAssignment> activeAssignments;
    private final Predicate<UUID> pending;

    DeliveryParcels(ComponentType<EntityStore, DeliveryAssignmentState> assignmentType,
        ComponentType<EntityStore, ThrownPresent> thrownPresentType,
        ComponentType<ChunkStore, DeliveryOwnership> ownershipType,
        ConcurrentMap<UUID, DeliveryAssignment> activeAssignments, Predicate<UUID> pending) {
        this.assignmentType = assignmentType;
        this.thrownPresentType = thrownPresentType;
        this.ownershipType = ownershipType;
        this.activeAssignments = activeAssignments;
        this.pending = pending;
    }

    void equip(Store<EntityStore> store, Ref<EntityStore> ref, DeliveryAssignment assignment) {
        Holder<ChunkStore> holder = ChunkStore.REGISTRY.newHolder();
        holder.putComponent(ownershipType, new DeliveryOwnership(assignment.session.identity()));
        store.addComponent(ref, CarriedBlock.getComponentType(), new CarriedBlock(PRESENT_BLOCK_ID, holder, null));
        store.addComponent(ref, assignmentType, new DeliveryAssignmentState(assignment.session.identity(), assignment.session.option()));
    }

    DeliveryIdentity identityOf(CarriedBlock block) {
        if (block == null || !PRESENT_BLOCK_ID.equals(block.getBlockTypeKey()) || block.getHolder() == null) {
            return null;
        }
        DeliveryOwnership ownership = block.getHolder().getComponent(ownershipType);
        return ownership == null ? null : ownership.identity();
    }

    DeliverySession session(DeliveryIdentity identity) {
        if (identity == null) {
            return null;
        }
        DeliveryAssignment assignment = activeAssignments.get(identity.ownerId());
        return assignment != null
            && assignment.session.matches(identity) ? assignment.session : null;
    }

    boolean isCurrentDrop(ThrownPresent parcel) {
        DeliverySession session = session(parcel.identity());
        return session != null && session.ownsDrop(parcel.identity(), parcel.dropId());
    }

    ThrownPresent beginThrow(Ref<EntityStore> player, ComponentAccessor<EntityStore> accessor) {
        CarriedBlock block = accessor.getComponent(player, CarriedBlock.getComponentType());
        DeliveryAssignmentState assignmentState = accessor.getComponent(player, assignmentType);
        PlayerRef playerRef = accessor.getComponent(player, PlayerRef.getComponentType());
        DeliveryIdentity identity = assignmentState == null ? null : assignmentState.identity();
        if (playerRef == null || identity == null
            || !playerRef.getUuid().equals(identity.ownerId())
            || !identity.equals(identityOf(block))) {
            return null;
        }
        DeliverySession session = session(identity);
        UUID token = session == null || pending.test(identity.ownerId()) ? null : session.drop(System.nanoTime());
        if (token != null) {
            DeliveryAssignment assignment = activeAssignments.get(identity.ownerId());
            TransformComponent position = accessor.getComponent(player, TransformComponent.getComponentType());
            if (assignment != null && position != null) {
                assignment.observeParcelPosition(position.getPosition());
            }
        }
        return token == null ? null : new ThrownPresent(block, identity, token);
    }

    boolean track(Ref<EntityStore> entity, ThrownPresent parcel) {
        if (parcel.identity() == null) {
            return false;
        }
        DeliveryAssignment assignment = activeAssignments.get(parcel.identity().ownerId());
        if (assignment == null) {
            return false;
        }
        synchronized (assignment.session) {
            if (!assignment.session.ownsDrop(parcel.identity(), parcel.dropId())) {
                return false;
            }
            assignment.parcelEntities.add(entity);
            TransformComponent transform = entity.getStore().getComponent(entity, TransformComponent.getComponentType());
            if (transform != null) {
                assignment.observeParcelPosition(transform.getPosition());
            }
            return true;
        }
    }

    void untrack(Ref<EntityStore> entity, ThrownPresent parcel) {
        if (parcel.identity() == null) {
            return;
        }
        DeliveryAssignment assignment = activeAssignments.get(parcel.identity().ownerId());
        if (assignment != null && assignment.session.identity().equals(parcel.identity())) {
            assignment.parcelEntities.remove(entity);
        }
    }

    boolean pickUp(Ref<EntityStore> player, Ref<EntityStore> target, CommandBuffer<EntityStore> commandBuffer) {
        if (!player.isValid() || !target.isValid() || player.getStore() != target.getStore()) {
            return false;
        }
        if (commandBuffer.getComponent(player, DeathComponent.getComponentType()) != null) {
            return false;
        }
        PlayerRef playerRef = commandBuffer.getComponent(player, PlayerRef.getComponentType());
        DeliveryAssignmentState assignmentState = commandBuffer.getComponent(player, assignmentType);
        ThrownPresent parcel = commandBuffer.getComponent(target, thrownPresentType);
        DeliveryIdentity identity = assignmentState == null ? null : assignmentState.identity();
        if (playerRef == null || pending.test(playerRef.getUuid()) || parcel == null || identity == null
            || !identity.equals(parcel.identity())
            || commandBuffer.getComponent(player, CarriedBlock.getComponentType()) != null) {
            return false;
        }
        TransformComponent playerPosition = commandBuffer.getComponent(player, TransformComponent.getComponentType());
        TransformComponent parcelPosition = commandBuffer.getComponent(target, TransformComponent.getComponentType());
        if (playerPosition == null || parcelPosition == null || playerPosition.getPosition().distanceSquared(parcelPosition.getPosition()) > MAX_PICKUP_DISTANCE_SQUARED
            || !pickupVisible(target.getStore().getExternalData().getWorld(), playerPosition.getPosition(), parcelPosition.getPosition())) {
            return false;
        }
        DeliverySession session = session(parcel.identity());
        if (session == null || !session.pickUp(playerRef.getUuid(), parcel.identity(), parcel.dropId(), System.nanoTime())) {
            return false;
        }
        commandBuffer.addComponent(player, CarriedBlock.getComponentType(), parcel.copyBlock());
        commandBuffer.removeEntity(target, RemoveReason.REMOVE);
        untrack(target, parcel);
        return true;
    }

    /** Lets the owner automatically pick up a nearby parcel on the ground. */
    void tryNearbyPickup(
        Ref<EntityStore> target,
        ThrownPresent parcel,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        DeliveryAssignment assignment = activeAssignments.get(parcel.identity().ownerId());
        if (assignment == null || !assignment.session.matches(parcel.identity())) {
            return;
        }
        TransformComponent position = commandBuffer.getComponent(target, TransformComponent.getComponentType());
        if (position != null) {
            assignment.observeParcelPosition(position.getPosition());
        }
        Ref<EntityStore> player = assignment.player.getReference();
        if (player == null || !player.isValid() || player.getStore() != target.getStore()) {
            return;
        }
        StandardPhysicsProvider physics = commandBuffer.getComponent(target, StandardPhysicsProvider.getComponentType());
        if (physics != null && !physics.isOnGround()) {
            return;
        }
        TransformComponent playerTransform = commandBuffer.getComponent(player, TransformComponent.getComponentType());
        if (playerTransform == null || position == null
            || !DeliveryPickupRange.contains(playerTransform.getPosition(), position.getPosition())) {
            return;
        }
        pickUp(player, target, commandBuffer);
    }

    /** Tracks a parcel dropped by Hytale and enables pickup. */
    void adoptNativeDrop(Ref<EntityStore> ref, CarriedBlock block, CommandBuffer<EntityStore> commandBuffer) {
        DeliveryIdentity identity = identityOf(block);
        if (identity == null) {
            return;
        }
        DeliverySession session = session(identity);
        UUID token = session == null ? null : session.drop(System.nanoTime());
        if (token == null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        ThrownPresent parcel = new ThrownPresent(block, identity, token);
        if (!track(ref, parcel)) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            return;
        }
        commandBuffer.putComponent(ref, thrownPresentType, parcel);
        commandBuffer.putComponent(ref, Interactions.getComponentType(), new Interactions(Map.of(InteractionType.Use, "Parcel_Pickup")));
        commandBuffer.ensureComponent(ref, Interactable.getComponentType());
        commandBuffer.ensureComponent(ref, EntityStore.REGISTRY.getNonSerializedComponentType());
    }

    private static boolean pickupVisible(World world, Vector3dc player, Vector3dc parcel) {
        Vector3d start = new Vector3d(player).add(0, 0.5, 0);
        Vector3d end = new Vector3d(parcel).add(0, 0.25, 0);
        Vector3d direction = new Vector3d(end).sub(start);
        double distance = direction.length();
        if (distance < 0.001) {
            return true;
        }
        direction.div(distance);
        return TargetUtil.getTargetBlock(world.getChunkStore(), (block, fluid) -> {
            BlockType type = BlockType.getAssetMap().getAsset(block);
            return type == null || type.getMaterial() == BlockMaterial.Solid;
        }, start.x, start.y, start.z, direction.x, direction.y, direction.z, distance) == null;
    }

    void removeStalePlayerState(
        Ref<EntityStore> ref,
        DeliveryAssignmentState assignmentState,
        CommandBuffer<EntityStore> commandBuffer
    ) {
        DeliveryIdentity identity = assignmentState.identity();
        CarriedBlock carriedBlock = commandBuffer.getComponent(ref, CarriedBlock.getComponentType());
        if (identity != null && identity.equals(identityOf(carriedBlock))) {
            commandBuffer.tryRemoveComponent(ref, CarriedBlock.getComponentType());
        }
        commandBuffer.tryRemoveComponent(ref, assignmentType);
    }

    void clearStaleState(Store<EntityStore> store, Ref<EntityStore> ref, UUID ownerId) {
        DeliveryAssignmentState assignmentState = store.getComponent(ref, assignmentType);
        DeliveryIdentity identity = assignmentState == null ? null : assignmentState.identity();
        if (assignmentState != null && (identity == null
            || (ownerId.equals(identity.ownerId()) && session(identity) == null))) {
            store.removeComponentIfExists(ref, assignmentType);
        }
        DeliveryIdentity carried = identityOf(store.getComponent(ref, CarriedBlock.getComponentType()));
        if (carried != null && ownerId.equals(carried.ownerId()) && session(carried) == null) {
            store.removeComponentIfExists(ref, CarriedBlock.getComponentType());
        }
    }

    void cleanup(DeliveryAssignment assignment) {
        DeliveryIdentity identity = assignment.session.identity();
        Ref<EntityStore> player = assignment.player.getReference();
        if (player != null) {
            onWorld(player, () -> {
                if (!player.isValid()) {
                    return;
                }
                Store<EntityStore> store = player.getStore();
                if (identity.equals(identityOf(store.getComponent(player, CarriedBlock.getComponentType())))) {
                    store.removeComponentIfExists(player, CarriedBlock.getComponentType());
                }
                DeliveryAssignmentState assignmentState = store.getComponent(player, assignmentType);
                if (assignmentState != null && identity.equals(assignmentState.identity())) {
                    store.removeComponentIfExists(player, assignmentType);
                }
            });
        }
        for (Ref<EntityStore> entity : assignment.parcelEntities) {
            onWorld(entity, () -> {
                if (!entity.isValid()) {
                    return;
                }
                Store<EntityStore> store = entity.getStore();
                ThrownPresent parcel = store.getComponent(entity, thrownPresentType);
                if ((parcel != null && identity.equals(parcel.identity()))
                    || identity.equals(identityOf(store.getComponent(entity, CarriedBlock.getComponentType())))) {
                    store.removeEntity(entity, RemoveReason.REMOVE);
                }
            });
        }
        assignment.parcelEntities.clear();
    }

    private static void onWorld(Ref<EntityStore> ref, Runnable action) {
        World world = ref.getStore().getExternalData().getWorld();
        if (world.isInThread()) {
            action.run();
        } else {
            // Skip cleanup if the world has stopped accepting changes.
            try {
                world.execute(action);
            } catch (RejectedExecutionException ignored) {
            }
        }
    }
}
