package acorn.thankmasdelivery.effect;

import acorn.thankmasdelivery.component.DeliveryAssignmentState;
import acorn.thankmasdelivery.component.DeliveryMovementState;

import com.hypixel.hytale.assetstore.map.AssetMapWithIndexes;
import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.builtin.weather.resources.WeatherResource;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.RemovalBehavior;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Adds, updates, and removes player effects when their delivery changes. */
public final class DeliveryEffectLifecycleSystem extends RefChangeSystem<EntityStore, DeliveryAssignmentState> {
    // Undo size changes before undoing the transformation they were applied to.
    private static final List<DeliveryEffect> REMOVAL_ORDER = List.of(DeliveryEffect.values()).reversed();
    private static final String BLINDNESS_WEATHER_ID = "Parcel_Blindness";
    private static final float BLINDNESS_TRANSITION_SECONDS = 0.15f;

    private final ComponentType<EntityStore, DeliveryAssignmentState> assignmentType;
    private final ComponentType<EntityStore, DeliveryMovementState> movementStateType;
    private final Query<EntityStore> query;

    public DeliveryEffectLifecycleSystem(
        @Nonnull ComponentType<EntityStore, DeliveryAssignmentState> assignmentType,
        @Nonnull ComponentType<EntityStore, DeliveryMovementState> movementStateType
    ) {
        this.assignmentType = assignmentType;
        this.movementStateType = movementStateType;
        query = Query.and(PlayerRef.getComponentType(), assignmentType);
    }

    @Nonnull
    @Override
    public ComponentType<EntityStore, DeliveryAssignmentState> componentType() {
        return assignmentType;
    }

    @Override
    public void onComponentAdded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        ensureEffects(ref, state, commandBuffer);
    }

    @Override
    public void onComponentSet(
        @Nonnull Ref<EntityStore> ref,
        @Nullable DeliveryAssignmentState oldState,
        @Nonnull DeliveryAssignmentState newState,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        if (oldState == null) {
            ensureEffects(ref, newState, commandBuffer);
            return;
        }
        newState.inheritRestoration(oldState);
        for (DeliveryEffect effect : REMOVAL_ORDER) {
            if (oldState.has(effect) && !newState.has(effect)) {
                remove(ref, oldState, effect, commandBuffer);
            }
        }
        ensureEffects(ref, newState, commandBuffer);
        resetMovementState(ref, commandBuffer);
    }

    @Override
    public void onComponentRemoved(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        for (DeliveryEffect effect : REMOVAL_ORDER) {
            if (state.has(effect)) {
                remove(ref, state, effect, commandBuffer);
            }
        }
        resetMovementState(ref, commandBuffer);
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    public static void ensureEffects(
        Ref<EntityStore> ref,
        DeliveryAssignmentState state,
        ComponentAccessor<EntityStore> accessor
    ) {
        for (DeliveryEffect effect : state.effects()) {
            apply(ref, state, effect, accessor);
        }
    }

    private static void apply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull DeliveryEffect effect,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (effect == DeliveryEffect.BIG || effect == DeliveryEffect.SMALL) {
            DeliverySizeEffects.apply(ref, state, accessor);
            return;
        }
        if (effect == DeliveryEffect.BLINDNESS) {
            applyBlindness(ref, state, accessor);
            return;
        }

        if (effect == DeliveryEffect.DASH_ONLY) {
            bindDash(ref, state, accessor);
        }

        String effectId = state.entityEffectId(effect);
        if (effectId == null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent controller = accessor.getComponent(
            ref,
            EffectControllerComponent.getComponentType()
        );
        if (entityEffect != null && controller != null && effect.isTransformation()) {
            DeliveryTransformationEffects.remember(ref, state, accessor);
        }
        if (entityEffect != null && controller != null
            && !controller.getActiveEffects().containsKey(EntityEffect.getAssetMap().getIndex(effectId))) {
            controller.addEffect(ref, entityEffect, accessor);
        }
        if (effect.isTransformation()) {
            ModelComponent model = accessor.getComponent(ref, ModelComponent.getComponentType());
            EntityScaleComponent scale = accessor.getComponent(ref, EntityScaleComponent.getComponentType());
            if (model != null && model.getModel() != null && state.transformationModelId().equals(model.getModel().getModelAssetId())) {
                state.transformationWaitTicks = 0;
                if (state.ownedTransformationScale == null) {
                    state.ownedTransformationScale = scale == null ? model.getModel().getScale() : scale.getScale();
                } else if (scale != null && Float.compare(scale.getScale(), state.ownedTransformationScale) != 0) {
                    state.effectConflict = true;
                }
            } else if (state.ownedTransformationScale != null || ++state.transformationWaitTicks > 20) {
                state.effectConflict = true;
            }
        }
    }

    private static void remove(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull DeliveryEffect effect,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        if (effect == DeliveryEffect.BIG || effect == DeliveryEffect.SMALL) {
            DeliverySizeEffects.restore(ref, state, accessor);
            return;
        }
        if (effect == DeliveryEffect.BLINDNESS) {
            removeBlindness(ref, state, accessor);
            return;
        }

        if (effect == DeliveryEffect.DASH_ONLY) {
            unbindDash(ref, state, accessor);
        }

        String effectId = state.entityEffectId(effect);
        if (effectId == null) {
            return;
        }

        EntityEffect entityEffect = EntityEffect.getAssetMap().getAsset(effectId);
        EffectControllerComponent controller = accessor.getComponent(
            ref,
            EffectControllerComponent.getComponentType()
        );
        if (effect.isTransformation()) {
            DeliveryTransformationEffects.remove(ref, state, effectId, accessor);
        } else if (entityEffect != null && controller != null) {
            controller.removeEffect(ref, EntityEffect.getAssetMap().getIndex(effectId), RemovalBehavior.COMPLETE, accessor);
        }
    }

    private static void applyBlindness(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        int weatherIndex = Weather.getAssetMap().getIndex(BLINDNESS_WEATHER_ID);
        if (weatherIndex == AssetMapWithIndexes.NOT_FOUND) {
            return;
        }

        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        WeatherTracker tracker = accessor.getComponent(ref, WeatherTracker.getComponentType());
        if (player == null || tracker == null) {
            return;
        }

        if (state.weatherOverrideOwner != null && tracker.getWeatherIndex() != weatherIndex) {
            state.effectConflict = true; return;
        }
        if (tracker.tryClaimOverride(ref, weatherIndex)) {
            state.weatherOverrideOwner = ref;
            tracker.sendWeatherIndex(player, weatherIndex, BLINDNESS_TRANSITION_SECONDS);
        } else {
            state.effectConflict = true;
        }
    }

    private static void removeBlindness(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull DeliveryAssignmentState state,
        @Nonnull ComponentAccessor<EntityStore> accessor
    ) {
        PlayerRef player = accessor.getComponent(ref, PlayerRef.getComponentType());
        WeatherTracker tracker = accessor.getComponent(ref, WeatherTracker.getComponentType());
        if (player == null || tracker == null) {
            return;
        }

        // Leave the weather alone if another plugin has replaced our blindness weather.
        if (tracker.getWeatherIndex() != Weather.getAssetMap().getIndex(BLINDNESS_WEATHER_ID)) {
            state.weatherOverrideOwner = null; return;
        }
        // Release the weather override with the same player reference that first claimed it.
        if (!tracker.releaseOverride(state.weatherOverrideOwner == null ? ref : state.weatherOverrideOwner)) {
            return;
        }
        state.weatherOverrideOwner = null;

        WeatherResource weather = accessor.getResource(WeatherResource.getResourceType());
        TransformComponent transform = accessor.getComponent(
            ref,
            TransformComponent.getComponentType()
        );
        if (weather != null && transform != null) {
            tracker.updateWeather(
                player,
                weather,
                transform,
                BLINDNESS_TRANSITION_SECONDS,
                accessor
            );
        }
    }

    private static void bindDash(
        Ref<EntityStore> ref,
        DeliveryAssignmentState state,
        ComponentAccessor<EntityStore> accessor
    ) {
        if (state.dashBound) {
            Interactions current = accessor.getComponent(ref, Interactions.getComponentType());
            if (current == null || !"Parcel_Dash".equals(current.getInteractionId(InteractionType.Secondary))) {
                state.effectConflict = true;
            }
            return;
        }
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        if (interactions == null) {
            interactions = new Interactions();
            accessor.putComponent(ref, Interactions.getComponentType(), interactions);
        }
        state.previousSecondaryInteractionId = interactions.getInteractionId(InteractionType.Secondary);
        interactions.setInteractionId(InteractionType.Secondary, "Parcel_Dash");
        state.dashBound = true;
    }

    private static void unbindDash(
        Ref<EntityStore> ref,
        DeliveryAssignmentState state,
        ComponentAccessor<EntityStore> accessor
    ) {
        if (!state.dashBound) {
            return;
        }
        Interactions interactions = accessor.getComponent(ref, Interactions.getComponentType());
        if (interactions != null && "Parcel_Dash".equals(interactions.getInteractionId(InteractionType.Secondary))) {
            if (state.previousSecondaryInteractionId == null) {
                interactions.removeInteractionId(InteractionType.Secondary);
            } else {
                interactions.setInteractionId(InteractionType.Secondary, state.previousSecondaryInteractionId);
            }
        }
        state.dashBound = false;
    }

    private void resetMovementState(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        DeliveryMovementState movementState = commandBuffer.getComponent(ref, movementStateType);
        if (movementState != null) {
            movementState.reset();
        }
    }
}
