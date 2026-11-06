package acorn.thankmasdelivery.interaction;

import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class InspectParcelInteraction extends ServerOnlyInteraction {
    private final DeliveryService service;

    public InspectParcelInteraction(DeliveryService service) {
        this.service = service;
    }

    public static BuilderCodec<InspectParcelInteraction> codec(DeliveryService service) {
        return BuilderCodec.builder(InspectParcelInteraction.class,
            () -> new InspectParcelInteraction(service), SimpleInstantInteraction.CODEC).build();
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        Ref<EntityStore> playerRef = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (playerRef == null || !playerRef.isValid() || commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        commandBuffer.run(store -> {
            PlayerRef player = store.getComponent(playerRef, PlayerRef.getComponentType());
            if (player != null && service.hasActiveDelivery(player.getUuid())) {
                service.openActiveDeliveryPage(store, playerRef, player);
            }
        });
    }
}
