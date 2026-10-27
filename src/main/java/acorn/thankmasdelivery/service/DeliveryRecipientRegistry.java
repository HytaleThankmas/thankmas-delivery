package acorn.thankmasdelivery.service;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.persistence.DeliveryStore;
import acorn.thankmasdelivery.persistence.DeliveryStore.Destination;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/** Tracks verified recipient entities and saves their movement and retirement. */
final class DeliveryRecipientRegistry {
    private record LiveRecipient(Ref<EntityStore> ref, UUID entityId, World world) { }
    private record RecipientRetirement(UUID entityId) { }
    private final ConcurrentMap<String, LiveRecipient> liveRecipients = new ConcurrentHashMap<>();
    private final Set<String> registeringRecipients = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<String, RecipientRetirement> recipientRetirements = new ConcurrentHashMap<>();
    private final Set<String> savingRetirements = ConcurrentHashMap.newKeySet();
    private final Set<String> recipientOperations = ConcurrentHashMap.newKeySet();
    private final DeliveryStore deliveries;
    private final DeliveryActions actions;
    private final Consumer<Throwable> failures;

    DeliveryRecipientRegistry(DeliveryStore deliveries, DeliveryActions actions, Consumer<Throwable> failures) {
        this.deliveries = deliveries;
        this.actions = actions;
        this.failures = failures;
    }

    boolean reserve(String id) {
        return recipientOperations.add(id);
    }
    void release(String id) {
        recipientOperations.remove(id);
    }
    void retryRetirements() {
        recipientRetirements.forEach(this::saveRetirement);
    }

    Ref<EntityStore> getLiveRef(String id) {
        LiveRecipient live = liveRecipients.get(id);
        return live != null && live.ref().isValid() ? live.ref() : null;
    }

    Set<String> eligible(World world) {
        Set<String> ids = new HashSet<String>();
        // Unloaded recipients can never receive a new parcel, so visit only live entities.
        for (Entry<String, LiveRecipient> entry : liveRecipients.entrySet()) {
            LiveRecipient live = entry.getValue();
            if (live.world() != world || !live.ref().isValid() || recipientRetirements.containsKey(entry.getKey())) {
                continue;
            }
            Destination destination = deliveries.recipient(entry.getKey());
            if (destination != null && !destination.disabled() && live.entityId().equals(destination.entityId())
                && world.getWorldConfig().getUuid().equals(destination.worldId())) {
                ids.add(destination.id());
            }
        }
        return Set.copyOf(ids);
    }

    boolean isRegistered(Ref<EntityStore> npc, DeliveryNpc marker) {
        Destination destination = deliveries.recipient(marker.id());
        if (destination == null || destination.disabled()) {
            return false;
        }
        UUIDComponent uuid = npc.getStore().getComponent(npc, UUIDComponent.getComponentType());
        return uuid != null && destination.entityId().equals(uuid.getUuid());
    }

    void forget(Ref<EntityStore> ref, DeliveryNpc marker) {
        // Do not keep the unloaded entity or its world alive through the recipient cache.
        liveRecipients.computeIfPresent(marker.id(), (id, live) -> live.ref().equals(ref) ? null : live);
    }

    void clear() {
        liveRecipients.clear();
        recipientRetirements.clear();
        recipientOperations.clear();
    }

    void removed(DeliveryNpc marker, UUID entityId, RemoveReason reason) {
        if (reason == RemoveReason.UNLOAD) {
            return;
        }
        if (!marker.recipient()) {
            return;
        }
        Destination destination = deliveries.recipient(marker.id());
        if (destination == null || destination.disabled()
            || !destination.entityId().equals(entityId)) {
            return;
        }
        RecipientRetirement request = new RecipientRetirement(destination.entityId());
        recipientRetirements.putIfAbsent(marker.id(), request);
        saveRetirement(marker.id(), request);
    }

    void observe(Ref<EntityStore> ref, DeliveryNpc marker) {
        if (!marker.recipient()) {
            return;
        }
        Destination destination = deliveries.recipient(marker.id());
        if (destination == null || destination.disabled()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        UUIDComponent uuid = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuid == null || !destination.entityId().equals(uuid.getUuid())) {
            return;
        }
        World world = store.getExternalData().getWorld();
        LiveRecipient live = liveRecipients.get(marker.id());
        if (live == null || !live.ref().equals(ref)) {
            liveRecipients.put(marker.id(), new LiveRecipient(ref, uuid.getUuid(), world));
        }
        String liveAppearance = acorn.thankmasdelivery.npc.DeliveryNpcAppearanceHelper.extractAppearanceFromRef(ref, store);
        if (liveAppearance != null) {
            acorn.thankmasdelivery.npc.DeliveryNpcAppearanceHelper.cacheAppearance(marker.id(), liveAppearance);
            acorn.thankmasdelivery.npc.DeliveryNpcAppearanceHelper.cacheAppearance(uuid.getUuid(), liveAppearance);
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || (world.getWorldConfig().getUuid().equals(destination.worldId())
            && Math.floor(destination.x()) == Math.floor(transform.getPosition().x())
            && Math.floor(destination.y()) == Math.floor(transform.getPosition().y())
            && Math.floor(destination.z()) == Math.floor(transform.getPosition().z()))) {
            return;
        }
        if (!registeringRecipients.add(marker.id())) {
            return;
        }
        Vector3d p = transform.getPosition();
        Destination updatedDestination = new DeliveryStore.Destination(destination.id(), marker.name(), world.getName(), p.x(), p.y(), p.z(),
            destination.hint(), uuid.getUuid(), world.getWorldConfig().getUuid(), false);
        actions.submit(() -> {
            Destination latest = deliveries.recipient(marker.id());
            if (latest != null && !latest.disabled() && latest.entityId().equals(updatedDestination.entityId())) {
                deliveries.register(new DeliveryStore.Destination(updatedDestination.id(), updatedDestination.name(), updatedDestination.world(),
                    updatedDestination.x(), updatedDestination.y(), updatedDestination.z(), latest.hint(), updatedDestination.entityId(),
                    updatedDestination.worldId(), false, latest.celebrationEmote()));
            }
            return true;
        }).whenComplete((ok, error) -> {
            registeringRecipients.remove(marker.id());
            if (error != null) {
                failures.accept(error);
            }
        });
    }

    private void saveRetirement(String id, RecipientRetirement request) {
        if (!savingRetirements.add(id)) {
            return;
        }
        actions.submit(() -> {
            Destination destination = deliveries.recipient(id);
            return destination == null || destination.disabled() || !Objects.equals(destination.entityId(), request.entityId())
                ? 0 : deliveries.retire(id);
        }).whenComplete((count, error) -> {
            savingRetirements.remove(id);
            if (error == null) {
                recipientRetirements.remove(id, request);
            } else {
                failures.accept(error);
            }
        });
    }
}
