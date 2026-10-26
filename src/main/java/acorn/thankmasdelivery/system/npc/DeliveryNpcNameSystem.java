package acorn.thankmasdelivery.system.npc;

import acorn.thankmasdelivery.component.DeliveryNpc;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.HolderSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import com.hypixel.hytale.server.npc.systems.RoleBuilderSystem;

import java.util.Set;

/** Restores the configured delivery name after Hytale sets up the NPC's role. */
public final class DeliveryNpcNameSystem extends HolderSystem<EntityStore> {
    private final ComponentType<EntityStore, DeliveryNpc> type;

    public DeliveryNpcNameSystem(ComponentType<EntityStore, DeliveryNpc> type) {
        this.type = type;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return type;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of(new SystemDependency<>(Order.AFTER, RoleBuilderSystem.class));
    }

    @Override
    public void onEntityAdd(Holder<EntityStore> holder, AddReason reason, Store<EntityStore> store) {
        DeliveryNpc marker = holder.getComponent(type);
        DisplayNameSupport.setDisplayName(holder, marker.name());
    }

    @Override
    public void onEntityRemoved(Holder<EntityStore> holder, RemoveReason reason, Store<EntityStore> store) { }
}
