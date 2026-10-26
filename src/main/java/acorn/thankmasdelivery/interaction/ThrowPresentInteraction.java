package acorn.thankmasdelivery.interaction;

import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.model.DeliverySession;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.modules.projectile.config.StandardPhysicsProvider;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import org.joml.Vector3d;

import javax.annotation.Nonnull;

/** Throws the player's parcel and checks whether it hits the assigned recipient. */
public final class ThrowPresentInteraction extends ServerOnlyInteraction {
    private final DeliveryService deliveryService;
    private final ComponentType<EntityStore, ThrownPresent> thrownPresentType;

    public ThrowPresentInteraction(DeliveryService deliveryService,
        ComponentType<EntityStore, ThrownPresent> thrownPresentType) {
        this.deliveryService = deliveryService;
        this.thrownPresentType = thrownPresentType;
    }

    public static BuilderCodec<ThrowPresentInteraction> codec(DeliveryService deliveryService,
        ComponentType<EntityStore, ThrownPresent> thrownPresentType) {
        return BuilderCodec.builder(ThrowPresentInteraction.class,
            () -> new ThrowPresentInteraction(deliveryService, thrownPresentType), SimpleInstantInteraction.CODEC).build();
    }

    @Override
    protected void firstRun(@Nonnull InteractionType type, @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> player = context.getEntity();
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset("Parcel_Projectile");
        if (config == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Transform look = TargetUtil.getLook(player, commandBuffer);
        ThrownPresent payload = deliveryService.beginThrow(player, commandBuffer);
        if (payload == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        final Ref<EntityStore> projectile;
        try {
            projectile = ProjectileModule.get().spawnProjectile(player, commandBuffer, config,
                new Vector3d(look.getPosition()), new Vector3d(look.getDirection()));
        } catch (RuntimeException error) {
            cancelThrow(payload);
            throw error;
        }

        // Add the owner and delivery IDs once Hytale has created the thrown parcel.
        commandBuffer.run(store -> {
            if (!projectile.isValid()) {
                cancelThrow(payload);
                return;
            }
            store.addComponent(projectile, thrownPresentType, payload);
            if (!deliveryService.track(projectile, payload)) {
                store.removeEntity(projectile, RemoveReason.REMOVE);
                return;
            }
            // Check again because the player may have stopped the delivery or picked up another block.
            if (player.isValid() && payload.identity().equals(deliveryService.identityOf(
                store.getComponent(player, CarriedBlock.getComponentType())))) {
                store.removeComponentIfExists(player, CarriedBlock.getComponentType());
            }
            store.ensureComponent(projectile, EntityStore.REGISTRY.getNonSerializedComponentType());
            store.ensureComponent(projectile, Interactable.getComponentType());
            store.removeComponentIfExists(projectile, DespawnComponent.getComponentType());
            StandardPhysicsProvider physics = store.getComponent(projectile, StandardPhysicsProvider.getComponentType());
            if (physics != null) {
                // Make the parcel land upright.
                physics.setImpactConsumer((ref, position, blockPos, target, detail, impactBuffer) -> {
                    if (target != null) {
                        impactBuffer.run(impactStore -> deliveryService.deliverThrownTo(ref, target));
                    }
                    if (!physics.isOnGround()) {
                        return;
                    }
                    // Set the parcel upright after Hytale finishes moving it for this update.
                    impactBuffer.run(landingStore -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        TransformComponent transform = landingStore.getComponent(ref, TransformComponent.getComponentType());
                        if (transform == null) {
                            return;
                        }
                        Rotation3f upright = new Rotation3f(0, transform.getRotation().yaw(), 0);
                        transform.setRotation(upright);
                        HeadRotation head = landingStore.getComponent(ref, HeadRotation.getComponentType());
                        if (head != null) {
                            head.setRotation(upright);
                        }
                    });
                });
                physics.setBounceConsumer(null);
            }
        });
    }

    private void cancelThrow(ThrownPresent payload) {
        DeliverySession session = deliveryService.session(payload.identity());
        if (session != null) {
            session.cancelDrop(payload.dropId());
        }
    }
}
