package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Remembers the player's original appearance and restores it when the transformation ends. */
public final class DeliveryTransformationEffects {
    private DeliveryTransformationEffects() { }

    public static void remember(Ref<EntityStore> ref, DeliveryAssignmentState state, ComponentAccessor<EntityStore> accessor) {
        if (state.previousModel != null) {
            return;
        }
        ModelComponent component = accessor.getComponent(ref, ModelComponent.getComponentType());
        if (component == null || component.getModel() == null) {
            return;
        }
        Model model = component.getModel();
        state.previousModel = model.toReference();
        EntityScaleComponent scale = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
        float savedScale = scale == null ? state.previousModel.getScale() : scale.getScale();
        state.previousScale = (savedScale > 0 && Float.isFinite(savedScale)) ? savedScale : 1f;
    }

    /** safely ends a transformation effect  */
    public static void remove(Ref<EntityStore> ref, DeliveryAssignmentState state, String effectId,
        ComponentAccessor<EntityStore> accessor) {
        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());

        //
        if (entityEffect != null && controller != null) {
            int effectIndex = EntityEffect.getAssetMap().getIndex(effectId);
            if (effectIndex != AssetMapWithIndexes.NOT_FOUND) {
                controller.removeEffect(ref, effectIndex, RemovalBehavior.COMPLETE, accessor);
            }
        }
        restore(ref, state, accessor);
    }


    /**
     * Restores an entity's model, scale, and bounding box
     * @param ref      the reference to the target entity within the entity store
     * @param state    the delivery assignment state containing snapshot data to restore and clear
     * @param accessor the component accessor used to retrieve, put, and modify entity components
     */
    private static void restore(Ref<EntityStore> ref, DeliveryAssignmentState state, ComponentAccessor<EntityStore> accessor) {
        float restoredScale = (state.previousScale > 0 && Float.isFinite(state.previousScale)) ? state.previousScale : 1f;
        PlayerSkinComponent skin = accessor.getComponent(ref, PlayerSkinComponent.getComponentType());

        if (skin != null) {
            // restore model and notify wardrobe
            boolean restoredPrevious = false;
            if (state.previousModel != null) {
                try {
                    Model model = state.previousModel.toModel();
                    if (model != null) {
                        accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
                        restoredPrevious = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (!restoredPrevious) {
                try {
                    Model playerModel = CosmeticsModule.get().createModel(skin.getPlayerSkin(), restoredScale);
                    if (playerModel != null) {
                        accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(playerModel));
                    }
                } catch (Exception ignored) {
                }
            }
            skin.setNetworkOutdated();
            notifyWardrobe(ref, accessor);
        } else if (state.previousModel != null) {
            // Non-player entities: restore from saved model reference
            Model model = state.previousModel.toModel();
            if (model != null) {
                accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
            }
        }

        // Restore entity scale
        EntityScaleComponent currentScale = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
        if (currentScale != null) {
            currentScale.setScale(restoredScale);
        } else {
            accessor.putComponent(ref, EntityScaleComponent.getComponentType(), new EntityScaleComponent(restoredScale));
        }

        // Restore bounding box scale
        BoundingBox bounds = accessor.getComponent(ref, BoundingBox.getComponentType());
        if (bounds != null) {
            bounds.setScale(1f);
        }

        state.previousModel = null;
        state.ownedTransformationScale = null;
    }


    /**
     * Notifies the player wardrobe component to rebuild its state for a player
     *
     * @param ref      the target entity
     * @param accessor the component accessor
     */
    @SuppressWarnings("unchecked")
    private static void notifyWardrobe(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        try {
            Class<?> compClass = Class.forName("dev.hardaway.wardrobe.impl.player.PlayerWardrobeComponent");
            java.lang.reflect.Method getTypeMethod = compClass.getMethod("getComponentType");
            com.hypixel.hytale.component.ComponentType<EntityStore, ?> compType =
                (com.hypixel.hytale.component.ComponentType<EntityStore, ?>) getTypeMethod.invoke(null);
            if (compType != null) {
                Object wardrobeComp = accessor.getComponent(ref, compType);
                if (wardrobeComp != null) {
                    java.lang.reflect.Method rebuildMethod = compClass.getMethod("rebuild");
                    rebuildMethod.invoke(wardrobeComp);
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
