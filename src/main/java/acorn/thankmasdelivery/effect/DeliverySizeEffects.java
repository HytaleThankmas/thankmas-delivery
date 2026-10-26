package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Changes the size of the delivery's Goblin or Kweebec form and restores it afterward. */
public final class DeliverySizeEffects {
    private DeliverySizeEffects() { }

    public static void apply(Ref<EntityStore> ref, DeliveryAssignmentState state, ComponentAccessor<EntityStore> accessor) {
        ModelComponent model = accessor.getComponent(ref, ModelComponent.getComponentType());
        if (model == null || model.getModel() == null) {
            return;
        }
        EntityScaleComponent current = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
        float modelScale = modelScale(ref, accessor);
        EntityScaleComponent scale = apply(state, current, model.getModel().getModelAssetId(), modelScale);
        if (scale == null) {
            return;
        }
        // Changing the visible size alone does not move the player's camera to match.
        // Give the model the same target size so the camera height matches, without multiplying the size twice.
        Model resized = resizeModel(model.getModel(), scale.getScale());
        if (resized == null) {
            state.effectConflict = true;
            return;
        }
        if (resized != model.getModel()) {
            accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(resized));
        }
        if (scale != current) {
            accessor.putComponent(ref, EntityScaleComponent.getComponentType(), scale);
        }
        updateBounds(ref, scale.getScale(), resized.getScale(), accessor);
    }

    public static void restore(Ref<EntityStore> ref, DeliveryAssignmentState state, ComponentAccessor<EntityStore> accessor) {
        EntityScaleComponent current = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
        ModelComponent model = accessor.getComponent(ref, ModelComponent.getComponentType());
        boolean ownsModelSize = state.formSizeApplied && model != null && model.getModel() != null
            && model.getModel().getModelAssetId().equals(state.transformationModelId())
            && Float.compare(model.getModel().getScale(), validScale(state.formBaseScale) * multiplier(state)) == 0;
        EntityScaleComponent scale = restore(state, current);
        if (scale == null) {
            return;
        }
        float restoredModelScale = modelScale(ref, accessor);
        if (ownsModelSize) {
            Model restored = resizeModel(model.getModel(), validScale(state.formBaseScale));
            if (restored != null) {
                accessor.putComponent(ref, ModelComponent.getComponentType(), new ModelComponent(restored));
                restoredModelScale = restored.getScale();
            }
        }
        // Send the restored size to clients even when it matches the model's default size.
        if (scale != current) {
            accessor.putComponent(ref, EntityScaleComponent.getComponentType(), scale);
        }
        updateBounds(ref, scale.getScale(), restoredModelScale, accessor);
    }

    static Model resizeModel(Model current, float targetScale) {
        if (Float.compare(current.getScale(), targetScale) == 0) {
            return current;
        }
        ModelAsset asset = ModelAsset.getAssetMap().getAsset(current.getModelAssetId());
        return asset == null ? null : Model.createScaledModel(asset, targetScale, current.getRandomAttachmentIds());
    }

    /** Calculates Big or Small from the Goblin or Kweebec form's starting size. */
    public static EntityScaleComponent apply(DeliveryAssignmentState state, EntityScaleComponent current, String modelId, float modelScale) {
        String expected = state.transformationModelId();
        if (expected == null || !expected.equals(modelId)
            || (!state.has(DeliveryEffect.BIG) && !state.has(DeliveryEffect.SMALL))) {
            return null;
        }
        if (state.formSizeApplied && current != null && state.ownedTransformationScale != null
            && Float.compare(current.getScale(), state.ownedTransformationScale) != 0) {
            state.effectConflict = true; return null;
        }
        if (!state.formSizeApplied) {
            state.formBaseScale = validScale(modelScale);
            state.formSizeApplied = true;
        }
        float target = validScale(state.formBaseScale) * multiplier(state);
        state.ownedTransformationScale = target;
        if (current == null) {
            return new EntityScaleComponent(target);
        }
        if (Float.compare(current.getScale(), target) != 0) {
            current.setScale(target);
        }
        return current;
    }

    /** Restores the starting size only if another plugin has not changed it. */
    public static EntityScaleComponent restore(DeliveryAssignmentState state, EntityScaleComponent current) {
        if (!state.formSizeApplied) {
            return null;
        }
        float original = validScale(state.formBaseScale);
        state.formSizeApplied = false;
        if (current == null) {
            state.ownedTransformationScale = original;
            return new EntityScaleComponent(original);
        }
        if (Float.compare(current.getScale(), original * multiplier(state)) != 0) {
            return null;
        }
        state.ownedTransformationScale = original;
        current.setScale(original);
        return current;
    }

    private static float multiplier(DeliveryAssignmentState state) {
        return state.has(DeliveryEffect.BIG) ? 1.5f : 0.5f;
    }

    private static float validScale(float scale) {
        return Float.isFinite(scale) && scale > 0 ? scale : 1f;
    }

    private static float modelScale(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        ModelComponent model = accessor.getComponent(ref, ModelComponent.getComponentType());
        return model == null || model.getModel() == null ? 1f : validScale(model.getModel().getScale());
    }

    private static void updateBounds(Ref<EntityStore> ref, float scale, float modelScale, ComponentAccessor<EntityStore> accessor) {
        BoundingBox box = accessor.getComponent(ref, BoundingBox.getComponentType());
        if (box != null) {
            box.setScale(scale / modelScale);
        }
    }
}
