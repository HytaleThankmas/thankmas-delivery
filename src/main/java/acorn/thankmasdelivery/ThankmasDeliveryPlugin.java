package acorn.thankmasdelivery;

import acorn.thankmasdelivery.asset.DeliveryAssets;
import acorn.thankmasdelivery.command.DeliveryCommand;
import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.component.DeliveryMovementState;
import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.component.DeliveryOwnership;
import acorn.thankmasdelivery.component.ThrownPresent;
import acorn.thankmasdelivery.effect.DeliveryEffectLifecycleSystem;
import acorn.thankmasdelivery.interaction.PickupPresentInteraction;
import acorn.thankmasdelivery.interaction.ThrowPresentInteraction;
import acorn.thankmasdelivery.npc.DeliveryNpcActionBuilder;
import acorn.thankmasdelivery.npc.DeliveryRecipientSensorBuilder;
import acorn.thankmasdelivery.persistence.DeliveryPersistenceIds;
import acorn.thankmasdelivery.persistence.DeliveryStore;
import acorn.thankmasdelivery.service.DeliveryService;
import acorn.thankmasdelivery.system.npc.DeliveryNpcRemovalSystem;
import acorn.thankmasdelivery.system.npc.DeliveryNpcTrackingSystem;
import acorn.thankmasdelivery.system.npc.DeliveryRecipientPromptSystem;
import acorn.thankmasdelivery.system.parcel.DeliveryGroundCleanupSystem;
import acorn.thankmasdelivery.system.parcel.DeliveryNativeDropSystem;
import acorn.thankmasdelivery.system.parcel.DeliveryParcelRemovalSystem;
import acorn.thankmasdelivery.system.player.DeliveryAssignmentSystem;
import acorn.thankmasdelivery.system.player.DeliveryMovementSystem;
import acorn.thankmasdelivery.system.player.DeliveryPlayerInitSystem;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.event.EventPriority;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Starts and stops the minigame, and registers its commands, components, and game systems.
 * @author Acorn
 */
public class ThankmasDeliveryPlugin extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private DeliveryService deliveryService;

    public ThankmasDeliveryPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        LOGGER.at(Level.INFO).log("[Thankmas Delivery] Setting up...");

        // Save NPC configuration and enough effect data to clean up after a crash.
        // Live deliveries themselves belong only to DeliveryService and DeliveryStore's session maps.
        ComponentType<EntityStore, DeliveryAssignmentState> assignmentType = getEntityStoreRegistry()
            .registerComponent(DeliveryAssignmentState.class, DeliveryPersistenceIds.ASSIGNMENT_COMPONENT, DeliveryAssignmentState.CODEC);
        ComponentType<EntityStore, DeliveryMovementState> movementStateType = getEntityStoreRegistry()
            .registerComponent(DeliveryMovementState.class, DeliveryMovementState::new);
        ComponentType<EntityStore, ThrownPresent> thrownPresentType = getEntityStoreRegistry()
            .registerComponent(ThrownPresent.class, ThrownPresent::new);
        ComponentType<ChunkStore, DeliveryOwnership> ownershipType = getChunkStoreRegistry()
            .registerComponent(DeliveryOwnership.class, DeliveryPersistenceIds.OWNERSHIP_COMPONENT, DeliveryOwnership.CODEC);
        ComponentType<EntityStore, DeliveryNpc> npcType = getEntityStoreRegistry()
            .registerComponent(DeliveryNpc.class, DeliveryPersistenceIds.NPC_COMPONENT, DeliveryNpc.CODEC);
        DeliveryStore deliveries = new DeliveryStore(getDataDirectory().resolveSibling(DeliveryPersistenceIds.DATA_DIRECTORY).resolve("deliveries.json"));
        deliveryService = new DeliveryService(assignmentType, thrownPresentType, ownershipType, npcType, deliveries);
        getCodecRegistry(Interaction.CODEC).register("thankmasDeliveryNpcUse",
            acorn.thankmasdelivery.interaction.DeliveryNpcInteraction.class,
            acorn.thankmasdelivery.interaction.DeliveryNpcInteraction.codec(deliveryService));
        getEntityStoreRegistry().registerSystem(new acorn.thankmasdelivery.system.npc.DeliveryNpcUseSystem(deliveryService));
        NPCPlugin.get().registerCoreComponentType("thankmasDeliveryNpcInteract", () -> new DeliveryNpcActionBuilder(deliveryService));
        NPCPlugin.get().registerCoreComponentType("thankmasDeliveryCanInteract", () -> new DeliveryRecipientSensorBuilder(deliveryService));
        getCodecRegistry(Interaction.CODEC).register("thankmasDeliveryThrow", ThrowPresentInteraction.class,
            ThrowPresentInteraction.codec(deliveryService, thrownPresentType));
        getCodecRegistry(Interaction.CODEC).register("thankmasDeliveryPickup", PickupPresentInteraction.class,
            PickupPresentInteraction.codec(deliveryService));

        getEntityStoreRegistry().registerSystem(new DeliveryPlayerInitSystem(movementStateType));
        getEntityStoreRegistry().registerSystem(new DeliveryParcelRemovalSystem(deliveryService, thrownPresentType));
        getEntityStoreRegistry().registerSystem(new DeliveryNpcTrackingSystem(deliveryService, npcType));
        getEntityStoreRegistry().registerSystem(new acorn.thankmasdelivery.system.npc.DeliveryNpcNameSystem(npcType));
        getEntityStoreRegistry().registerSystem(new DeliveryNpcRemovalSystem(deliveryService, npcType));
        // World transfers end the old delivery, even if the player remains connected.
        getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, event -> {
            PlayerRef player = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (player != null) {
                deliveryService.joinedWorld(player);
            }
        });
        getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, event -> {
            PlayerRef player = event.getHolder().getComponent(PlayerRef.getComponentType());
            if (player != null) {
                deliveryService.leftWorld(player);
            }
        });
        getEntityStoreRegistry().registerSystem(
            new DeliveryEffectLifecycleSystem(assignmentType, movementStateType)
        );
        getEntityStoreRegistry().registerSystem(new DeliveryNativeDropSystem(deliveryService));
        getEntityStoreRegistry().registerSystem(new DeliveryGroundCleanupSystem(deliveryService, thrownPresentType));
        getEntityStoreRegistry().registerSystem(new DeliveryAssignmentSystem(deliveryService, assignmentType));
        getEntityStoreRegistry().registerSystem(new DeliveryRecipientPromptSystem(deliveryService));
        getEntityStoreRegistry().registerSystem(new DeliveryMovementSystem(assignmentType, movementStateType));
        getCommandRegistry().registerCommand(new DeliveryCommand(deliveryService));
        // End the delivery before Hytale drops the disconnecting player's carried block.
        getEventRegistry().register(EventPriority.FIRST, PlayerDisconnectEvent.class,
            event -> deliveryService.disconnect(event.getPlayerRef()));

        LOGGER.at(Level.INFO).log("[Thankmas Delivery] Setup complete!");
    }

    @Override
    protected void start() {
        List<String> missing = DeliveryAssets.missing();
        if (!missing.isEmpty()) {
            LOGGER.atSevere().log("Deliveries disabled: missing assets %s", missing);
        }
        LOGGER.at(Level.INFO).log("[Thankmas Delivery] Started!");
    }

    @Override
    protected void shutdown() {
        if (deliveryService != null) {
            deliveryService.shutdown();
        }
        LOGGER.at(Level.INFO).log("[Thankmas Delivery] Shutting down...");
    }
}
