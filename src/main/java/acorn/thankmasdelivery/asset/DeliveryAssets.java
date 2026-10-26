package acorn.thankmasdelivery.asset;

import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.effect.DeliveryForms;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.npc.NPCPlugin;

import java.util.ArrayList;
import java.util.List;

public final class DeliveryAssets {
    private static final long CACHE_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    private record Check(long time, List<String> missing) { }
    private static volatile Check lastCheck;
    private DeliveryAssets() { }

    public static List<String> missing() {
        long now = System.nanoTime();
        Check cached = lastCheck;
        if (cached != null && now - cached.time() < CACHE_NANOS) {
            return cached.missing();
        }
        return refresh(now);
    }

    private static synchronized List<String> refresh(long now) {
        Check cached = lastCheck;
        if (cached == null || now - cached.time() >= CACHE_NANOS) {
            // All couriers share one scan. Asset reloads are noticed within two seconds.
            lastCheck = new Check(now, findMissing());
        }
        return lastCheck.missing();
    }

    private static List<String> findMissing() {
        List<String> missing = new ArrayList<String>();
        if (BlockType.getAssetMap().getAsset("Parcel") == null) {
            missing.add("block Parcel");
        }
        if (ProjectileConfig.getAssetMap().getAsset("Parcel_Projectile") == null) {
            missing.add("projectile Parcel_Projectile");
        }
        if (Weather.getAssetMap().getAsset("Parcel_Blindness") == null) {
            missing.add("weather Parcel_Blindness");
        }
        for (DeliveryEffect effect : DeliveryEffect.values()) {
            String id = effect.entityEffectId();
            if (id != null && EntityEffect.getAssetMap().getAsset(id) == null) {
                missing.add("effect " + id);
            }
        }
        for (String model : DeliveryForms.MODEL_IDS) {
            if (ModelAsset.getAssetMap().getAsset(model) == null) {
                missing.add("model " + model);
            }
            if (EntityEffect.getAssetMap().getAsset("Parcel_Effect_" + model) == null) {
                missing.add("effect Parcel_Effect_" + model);
            }
        }
        for (String root : List.of("Parcel_Delivery_NPC", "Parcel_Throw", "Parcel_Pickup", "Parcel_Dash")) {
            if (com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap().getAsset(root) == null) {
                missing.add("root interaction " + root);
            }
        }
        if (ModelAsset.getAssetMap().getAsset("Parcel_Projectile") == null) {
            missing.add("model Parcel_Projectile");
        }
        for (String role : List.of("Parcel_Dispatcher", "Parcel_Recipient")) {
            if (NPCPlugin.get().getIndex(role) < 0) {
                missing.add("role " + role);
            }
        }
        return List.copyOf(missing);
    }
}
