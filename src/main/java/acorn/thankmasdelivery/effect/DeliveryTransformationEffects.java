package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
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
        state.previousScale = scale == null ? state.previousModel.getScale() : scale.getScale();
    }

    /** safely ends a transformation effect  */
    public static void remove(Ref<EntityStore> ref, DeliveryAssignmentState state, String effectId,
        ComponentAccessor<EntityStore> accessor) {
        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent controller = accessor.getComponent(ref, EffectControllerComponent.getComponentType());
        ComponentType<EntityStore, ModelComponent> modelType = ModelComponent.getComponentType();
        ComponentType<EntityStore, EntityScaleComponent> scaleType = EntityScaleComponent.getComponentType();
        ModelComponent currentModel = accessor.getComponent(ref, modelType);
        EntityScaleComponent currentScale = accessor.getComponent(ref, scaleType);
        String expectedModel = state.transformationModelId();
        boolean foreignModel = expectedModel != null && currentModel != null && currentModel.getModel() != null
            && !expectedModel.equals(currentModel.getModel().getModelAssetId())
            && state.previousModel != null
            && !state.previousModel.getModelAssetId().equals(currentModel.getModel().getModelAssetId());
        Model preservedModel = foreignModel ? currentModel.getModel() : null;
        Float preservedScale = currentScale != null && state.ownedTransformationScale != null
            && Float.compare(currentScale.getScale(), state.ownedTransformationScale) != 0
            && (!state.formSizeApplied || Float.compare(currentScale.getScale(), state.formBaseScale) != 0)
            ? currentScale.getScale() : null;

        if (entityEffect != null && controller != null) {
            controller.removeEffect(ref, EntityEffect.getAssetMap().getIndex(effectId), RemovalBehavior.COMPLETE, accessor);
        }
        restore(ref, state, accessor);
        if (preservedModel != null) {
            accessor.putComponent(ref, modelType, new ModelComponent(preservedModel));
        }
        if (preservedScale != null) {
            accessor.putComponent(ref, scaleType, new EntityScaleComponent(preservedScale));
        }
        ModelComponent model = accessor.getComponent(ref, modelType);
        EntityScaleComponent scale = accessor.getComponent(ref, scaleType);
        BoundingBox bounds = accessor.getComponent(ref, BoundingBox.getComponentType());
        if (bounds != null && model != null && model.getModel() != null && scale != null) {
            bounds.setScale(scale.getScale() / model.getModel().getScale());
        }
    }

    private static void restore(Ref<EntityStore> ref, DeliveryAssignmentState state, ComponentAccessor<EntityStore> accessor) {
        ModelComponent current = accessor.getComponent(ref, ModelComponent.getComponentType());
        if (current == null || current.getModel() == null) {
            return;
        }
        if (state.previousModel != null && isTransformation(current.getModel().getModelAssetId())) {
            Model model = state.previousModel.toModel();
            if (model != null) {
                accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(model));
            }
        }
        if (state.previousModel != null) {
            EntityScaleComponent scale = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
            boolean ownsScale = scale == null || state.ownedTransformationScale == null
                || Float.compare(scale.getScale(), state.ownedTransformationScale) == 0
                || (state.formBaseScale > 0 && Float.compare(scale.getScale(), state.formBaseScale) == 0);
            if (ownsScale && scale == null) {
                accessor.putComponent(ref, EntityScaleComponent.getComponentType(), new EntityScaleComponent(state.previousScale));
            } else if (ownsScale) {
                scale.setScale(state.previousScale);
            }
            ModelComponent model = accessor.getComponent(ref, ModelComponent.getComponentType());
            BoundingBox bounds = accessor.getComponent(ref, BoundingBox.getComponentType());
            if (bounds != null && model != null && model.getModel() != null) {
                bounds.setScale((ownsScale ? state.previousScale : scale.getScale()) / model.getModel().getScale());
            }
        }
        PlayerSkinComponent skin = accessor.getComponent(ref, PlayerSkinComponent.getComponentType());
        if (skin != null) {
            skin.setNetworkOutdated();
        }
        state.previousModel = null;
        state.ownedTransformationScale = null;
    }

    private static boolean isTransformation(String id) {
        return DeliveryForms.MODEL_IDS.contains(id);
    }
}
