package acorn.thankmasdelivery.npc;

import acorn.thankmasdelivery.persistence.DeliveryStore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.Role;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


public final class DeliveryNpcAppearanceHelper {

    public static final String NPC_MEMORIES_IMAGE_DIR = "Icons/ModelsGenerated/";
    public static final String DEFAULT_FALLBACK_NPC = "Kweebec_Sapling";


    private static final ConcurrentMap<UUID, String> UUID_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> ID_CACHE = new ConcurrentHashMap<>();
    private DeliveryNpcAppearanceHelper() {
    }


    public static void clearCache() {
        UUID_CACHE.clear();
        ID_CACHE.clear();
    }

    public static void cacheAppearance(@Nullable String key, @Nullable String appearance) {
        if (key != null && !key.isBlank() && appearance != null && !appearance.isBlank()) {
            ID_CACHE.put(key, appearance);
        }
    }

    public static void cacheAppearance(@Nullable UUID uuid, @Nullable String appearance) {
        if (uuid != null && appearance != null && !appearance.isBlank()) {
            UUID_CACHE.put(uuid, appearance);
        }
    }


    @Nullable
    public static String extractAppearanceFromRef(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return null;
        }

        try {
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                Role role = npc.getRole();
                if (role != null) {
                    String memoriesOverride = role.getMemoriesNameOverride();
                    if (memoriesOverride != null && !memoriesOverride.isBlank()) {
                        return memoriesOverride;
                    }
                    String appearance = role.getAppearanceName();
                    if (appearance != null && !appearance.isBlank()) {
                        return appearance;
                    }
                }
                String roleName = npc.getRoleName();
                if (roleName != null && !roleName.isBlank()) {
                    return roleName;
                }
            }

            ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
            if (modelComponent != null && modelComponent.getModel() != null) {
                String modelAssetId = modelComponent.getModel().getModelAssetId();
                if (modelAssetId != null && !modelAssetId.isBlank()) {
                    return modelAssetId;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }


    @Nullable
    public static String getAppearance(@Nullable DeliveryStore.Destination destination) {
        if (destination == null) {
            return null;
        }
        UUID entityId = destination.entityId();
        String destId = destination.id();
        if (entityId != null) {
            String cached = UUID_CACHE.get(entityId);
            if (cached != null) return cached;
        }
        if (destId != null) {
            String cached = ID_CACHE.get(destId);
            if (cached != null) return cached;
        }

        String resolved = null;
        try {
            if (destination.worldId() != null && entityId != null) {
                World world = Universe.get().getWorld(destination.worldId());
                if (world != null) {
                    Ref<EntityStore> ref = world.getEntityRef(entityId);
                    if (ref != null && ref.isValid()) {
                        resolved = extractAppearanceFromRef(ref, ref.getStore());
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (resolved == null || resolved.isBlank()) {
            if (destination.name() != null && !destination.name().isBlank()) {
                resolved = destination.name();
            } else {
                resolved = destId;
            }
        }

        if (resolved != null && !resolved.isBlank()) {
            cacheAppearance(entityId, resolved);
            cacheAppearance(destId, resolved);
        }

        return resolved;
    }


    @Nonnull
    public static String resolveNpcAssetPath(@Nullable DeliveryStore.Destination destination) {
        String appearance = getAppearance(destination);
        return resolveNpcAssetPath(appearance);
    }

    @Nonnull
    public static String resolveNpcAssetPath(@Nullable String appearanceOrName) {
        String name = sanitizeName(appearanceOrName);
        if (name == null || name.isBlank()) {
            name = DEFAULT_FALLBACK_NPC;
        }

        return NPC_MEMORIES_IMAGE_DIR + name + ".png";
    }


    @Nullable
    private static String sanitizeName(@Nullable String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim();

        if (clean.regionMatches(true, clean.length() - 4, ".png", 0, 4)) {
            clean = clean.substring(0, clean.length() - 4);
        }

        int lastSlash = Math.max(clean.lastIndexOf('/'), clean.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            clean = clean.substring(lastSlash + 1);
        }

        return clean.trim();
    }
}
