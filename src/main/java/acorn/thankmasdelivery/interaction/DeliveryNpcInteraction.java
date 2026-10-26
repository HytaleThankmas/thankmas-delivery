package acorn.thankmasdelivery.interaction;

import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;

public final class DeliveryNpcInteraction extends ServerOnlyInteraction {
    private final DeliveryService service;

    public DeliveryNpcInteraction(DeliveryService service) {
        this.service = service;
    }

    public static BuilderCodec<DeliveryNpcInteraction> codec(DeliveryService service) {
        return BuilderCodec.builder(DeliveryNpcInteraction.class,
            () -> new DeliveryNpcInteraction(service), SimpleInstantInteraction.CODEC).build();
    }

    @Override
    protected void firstRun(InteractionType type, InteractionContext context, CooldownHandler cooldown) {
        if (context.getCommandBuffer() == null || !service.queueNpcInteraction(context.getEntity(), context.getTargetEntity())) {
            context.getState().state = InteractionState.Failed;
        }
    }
}
