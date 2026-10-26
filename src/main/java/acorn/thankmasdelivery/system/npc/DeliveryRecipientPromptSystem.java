package acorn.thankmasdelivery.system.npc;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.dependency.SystemGroupDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.ComponentUpdate;
import com.hypixel.hytale.protocol.ComponentUpdateType;
import com.hypixel.hytale.protocol.InteractableUpdate;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityUpdate;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.EntityViewer;
import com.hypixel.hytale.server.core.modules.entity.tracker.EntityTrackerSystems.Visible;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Shows each player the right delivery prompt while keeping unrelated NPC prompts available. */
public final class DeliveryRecipientPromptSystem extends EntityTickingSystem<EntityStore> {
    private static final Set<Dependency<EntityStore>> DEPENDENCIES = Set.of(
        new SystemGroupDependency<>(Order.AFTER, EntityTrackerSystems.QUEUE_UPDATE_GROUP),
        new SystemDependency<>(Order.BEFORE, EntityTrackerSystems.SendPackets.class)
    );

    private static final long CLEANUP_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final DeliveryService service;
    private final Query<EntityStore> query;

    private static final class ViewerPrompts {
        final Set<Ref<EntityStore>> shown = ConcurrentHashMap.newKeySet();
        volatile long nextCleanup;
    }
    private final Map<EntityTrackerSystems.EntityViewer, ViewerPrompts> shownPrompts = Collections.synchronizedMap(new WeakHashMap<>());

    public DeliveryRecipientPromptSystem(DeliveryService service) {
        this.service = service;
        this.query = Query.and(service.npcType(), EntityTrackerSystems.Visible.getComponentType());
    }

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return DEPENDENCIES;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        DeliveryNpc marker = chunk.getComponent(index, service.npcType());
        if (marker == null) {
            return;
        }
        Ref<EntityStore> npc = chunk.getReferenceTo(index);
        Visible visible = chunk.getComponent(index, EntityTrackerSystems.Visible.getComponentType());
        Interactions interactions = store.getComponent(npc, Interactions.getComponentType());
        boolean hasOtherUse = interactions != null && interactions.getInteractionId(InteractionType.Use) != null
            && !"Parcel_Delivery_NPC".equals(interactions.getInteractionId(InteractionType.Use));
        long now = System.nanoTime();
        for (Entry<Ref<EntityStore>, EntityViewer> entry : visible.visibleTo.entrySet()) {
            EntityViewer viewer = entry.getValue();
            boolean eligible = service.canInteractNpc(entry.getKey(), npc);
            ViewerPrompts prompts = shownPrompts.computeIfAbsent(viewer, ignored -> new ViewerPrompts());
            Set<Ref<EntityStore>> shown = prompts.shown;
            // Sweep once per viewer per second, not once for every visible NPC on every tick.
            if (now >= prompts.nextCleanup) {
                prompts.nextCleanup = now + CLEANUP_INTERVAL_NANOS;
                shown.removeIf(ref -> !ref.isValid() || !viewer.visible.contains(ref));
            }
            boolean wasShown = shown.contains(npc);
            // Show the NPC's usual prompt when this player has no delivery interaction available.
            if (!eligible && hasOtherUse) {
                if (wasShown) {
                    viewer.updates.put(npc, filterPrompt(viewer.updates.get(npc), true,
                        interactions.getInteractionHint()));
                }
                shown.remove(npc);
                continue;
            }
            EntityUpdate pending = viewer.updates.get(npc);
            if (eligible != wasShown || pending != null) {
                viewer.updates.put(npc, filterPrompt(pending, eligible,
                    "server.interactionHints.Parcel_" + (marker.recipient() ? "Recipient" : "Dispatcher")));
            }
            if (eligible) {
                shown.add(npc);
            } else {
                shown.remove(npc);
            }
        }
    }

    private static EntityTrackerSystems.EntityUpdate filterPrompt(EntityTrackerSystems.EntityUpdate original, boolean eligible, String hint) {
        EntityUpdate filtered = new EntityTrackerSystems.EntityUpdate();
        if (original != null) {
            ComponentUpdateType[] removed = original.toRemovedArray();
            if (removed != null) {
                for (ComponentUpdateType type : removed) {
                    if (type != ComponentUpdateType.Interactable) {
                        filtered.queueRemove(type);
                    }
                }
            }
            ComponentUpdate[] updates = original.toUpdatesArray();
            if (updates != null) {
                for (ComponentUpdate update : updates) {
                    if (!(update instanceof InteractableUpdate)) {
                        filtered.queueUpdate(update);
                    }
                }
            }
        }
        if (eligible) {
            filtered.queueUpdate(new InteractableUpdate(hint));
        } else {
            filtered.queueRemove(ComponentUpdateType.Interactable);
        }
        return filtered;
    }
}
