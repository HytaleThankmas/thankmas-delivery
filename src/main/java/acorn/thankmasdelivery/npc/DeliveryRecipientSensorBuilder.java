package acorn.thankmasdelivery.npc;

import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.corecomponents.interaction.SensorCanInteract;
import com.hypixel.hytale.server.npc.corecomponents.interaction.builders.BuilderSensorCanInteract;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.instructions.Sensor;

public final class DeliveryRecipientSensorBuilder extends BuilderSensorCanInteract {
    private final DeliveryService service;

    public DeliveryRecipientSensorBuilder(DeliveryService service) {
        this.service = service;
    }

    @Override
    public Sensor build(BuilderSupport support) {
        return new SensorCanInteract(this, support) {
            @Override
            public boolean matches(Ref<EntityStore> npc, ExecutionSupport execution,
                double dt, Store<EntityStore> store) {
                return super.matches(npc, execution, dt, store)
                    && service.canInteractNpc(execution.getStateSupport().getInteractionIterationTarget(), npc);
            }
        };
    }
}
