package acorn.thankmasdelivery.npc;

import acorn.thankmasdelivery.service.DeliveryService;

import com.google.gson.JsonElement;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.asset.builder.Builder;
import com.hypixel.hytale.server.npc.asset.builder.BuilderDescriptorState;
import com.hypixel.hytale.server.npc.asset.builder.BuilderSupport;
import com.hypixel.hytale.server.npc.asset.builder.InstructionType;
import com.hypixel.hytale.server.npc.corecomponents.ActionBase;
import com.hypixel.hytale.server.npc.corecomponents.builders.BuilderActionBase;
import com.hypixel.hytale.server.npc.instructions.Action;
import com.hypixel.hytale.server.npc.instructions.ExecutionSupport;
import com.hypixel.hytale.server.npc.sensorinfo.InfoProvider;

import java.util.EnumSet;

public final class DeliveryNpcActionBuilder extends BuilderActionBase {
    private final DeliveryService service;
    public DeliveryNpcActionBuilder(DeliveryService service) {
        this.service = service;
    }
    @Override
    public String getShortDescription() {
        return "Talk to a Thankmas dispatcher or hand over a present";
    }
    @Override
    public String getLongDescription() {
        return getShortDescription();
    }
    @Override
    public BuilderDescriptorState getBuilderDescriptorState() {
        return BuilderDescriptorState.Stable;
    }
    @Override
    public Builder<Action> readConfig(JsonElement data) {
        requireInstructionType(EnumSet.of(InstructionType.Interaction));
        return this;
    }
    @Override
    public Action build(BuilderSupport support) {
        return new ActionBase(this) {
            @Override
            public void registerWithSupport(ExecutionSupport execution) {
                super.registerWithSupport(execution);
                // Check for players within six blocks so the F prompt works at a specific range
                execution.getPositionCache().requirePlayerDistanceUnsorted(6);
            }
            @Override
            public boolean canExecute(Ref<EntityStore> ref, ExecutionSupport execution, InfoProvider sensor,
                double dt, Store<EntityStore> store) {
                return super.canExecute(ref, execution, sensor, dt, store)
                    && service.canInteractNpc(execution.getStateSupport().getInteractionIterationTarget(), ref);
            }
            @Override
            public boolean execute(Ref<EntityStore> npc, ExecutionSupport execution, InfoProvider sensor,
                double dt, Store<EntityStore> store) {
                super.execute(npc, execution, sensor, dt, store);
                Ref<EntityStore> target = execution.getStateSupport().getInteractionIterationTarget();
                return service.queueNpcInteraction(target, npc);
            }
        };
    }
}
