package acorn.thankmasdelivery.npc;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.persistence.DeliveryStore;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
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

    public static final String PLAYER_RESOURCE_DIR = "Common/UI/Custom/Delivery/ModelsGenerated/";
    public static final String PLAYER_ICON_DIR = "UI/Custom/Delivery/ModelsGenerated/";
    public static final String NPC_ICON_DIR = "Icons/ModelsGenerated/";
    public static final String DEFAULT_FALLBACK_NPC = "Kweebec_Sapling";
    public static final String DEFAULT_FALLBACK_NPC_PATH = NPC_ICON_DIR + DEFAULT_FALLBACK_NPC + ".png";

    private static final ConcurrentMap<UUID, String> UUID_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, String> ID_CACHE = new ConcurrentHashMap<>();

    private DeliveryNpcAppearanceHelper() {
    }


    public static void clearCache() {
        UUID_CACHE.clear();
        ID_CACHE.clear();
    }

    public static void cacheAppearance(@Nullable String key, @Nullable String assetPath) {
        if (key != null && !key.isBlank() && assetPath != null && !assetPath.isBlank()) {
            ID_CACHE.put(key, assetPath);
        }
    }

    public static void cacheAppearance(@Nullable UUID uuid, @Nullable String assetPath) {
        if (uuid != null && assetPath != null && !assetPath.isBlank()) {
            UUID_CACHE.put(uuid, assetPath);
        }
    }


    @Nullable
    public static String extractAppearanceFromRef(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        if (ref == null || !ref.isValid() || store == null) {
            return null;
        }

        try {
            boolean isPlayerNpc = store.getComponent(ref, PlayerSkinComponent.getComponentType()) != null;
            if (!isPlayerNpc) {
                ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
                if (modelComponent != null && modelComponent.getModel() != null) {
                    isPlayerNpc = "Player".equalsIgnoreCase(modelComponent.getModel().getModelAssetId());
                }
            }

            if (isPlayerNpc) {
                DeliveryNpc marker = store.getComponent(ref, DeliveryNpc.getComponentType());
                String name = marker != null ? marker.name() : null;
                if (name == null || name.isBlank()) {
                    DisplayNameComponent displayName = store.getComponent(ref, DisplayNameComponent.getComponentType());
                    if (displayName != null && displayName.getDisplayName() != null) {
                        name = displayName.getDisplayName().getRawText();
                    }
                }
                if (name != null && !name.isBlank()) {
                    return PLAYER_ICON_DIR + sanitizeName(name) + ".png";
                }
            }

            // Non-Player NPC -> Icons/ModelsGenerated/<Appearance>.png
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (npc != null) {
                Role role = npc.getRole();
                if (role != null) {
                    String memoriesOverride = role.getMemoriesNameOverride();
                    if (memoriesOverride != null && !memoriesOverride.isBlank()) {
                        return NPC_ICON_DIR + sanitizeName(memoriesOverride) + ".png";
                    }
                    String appearance = role.getAppearanceName();
                    if (appearance != null && !appearance.isBlank()) {
                        return NPC_ICON_DIR + sanitizeName(appearance) + ".png";
                    }
                }
                String roleName = npc.getRoleName();
                if (roleName != null && !roleName.isBlank()) {
                    return NPC_ICON_DIR + sanitizeName(roleName) + ".png";
                }
            }

            ModelComponent modelComponent = store.getComponent(ref, ModelComponent.getComponentType());
            if (modelComponent != null && modelComponent.getModel() != null) {
                String modelAssetId = modelComponent.getModel().getModelAssetId();
                if (modelAssetId != null && !modelAssetId.isBlank()) {
                    return NPC_ICON_DIR + sanitizeName(modelAssetId) + ".png";
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
            String name = destination.name() != null && !destination.name().isBlank() ? destination.name() : destId;
            resolved = resolveNpcAssetPath(name);
        }

        if (resolved != null && !resolved.isBlank()) {
            cacheAppearance(entityId, resolved);
            cacheAppearance(destId, resolved);
        }

        return resolved;
    }

    @Nonnull
    public static String resolveNpcAssetPath(@Nullable DeliveryStore.Destination destination) {
        String path = getAppearance(destination);
        return (path != null && !path.isBlank()) ? path : DEFAULT_FALLBACK_NPC_PATH;
    }

    @Nonnull
    public static String resolveNpcAssetPath(@Nullable String appearanceOrName) {
        if (appearanceOrName == null || appearanceOrName.isBlank()) {
            return DEFAULT_FALLBACK_NPC_PATH;
        }

        if (appearanceOrName.startsWith("UI/") || appearanceOrName.startsWith("Icons/")) {
            return appearanceOrName;
        }

        String clean = sanitizeName(appearanceOrName);
        if (clean == null || clean.isBlank()) {
            return DEFAULT_FALLBACK_NPC_PATH;
        }

        if (DeliveryNpcAppearanceHelper.class.getResource("/" + PLAYER_RESOURCE_DIR + clean + ".png") != null) {
            return PLAYER_ICON_DIR + clean + ".png";
        }

        return NPC_ICON_DIR + clean + ".png";
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
