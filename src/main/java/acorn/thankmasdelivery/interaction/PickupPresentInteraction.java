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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PickupPresentInteraction extends ServerOnlyInteraction {
    private final DeliveryService deliveryService;

    public PickupPresentInteraction(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    public static BuilderCodec<PickupPresentInteraction> codec(DeliveryService deliveryService) {
        return BuilderCodec.builder(PickupPresentInteraction.class,
            () -> new PickupPresentInteraction(deliveryService), SimpleInstantInteraction.CODEC).build();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldownHandler) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Ref<EntityStore> target = context.getTargetEntity();
        if (commandBuffer == null || target == null || !deliveryService.pickUp(context.getEntity(), target, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
        }
    }
}
