package acorn.thankmasdelivery.component;

import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.model.DeliveryIdentity;
import acorn.thankmasdelivery.model.DeliveryOption;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class DeliveryAssignmentState implements Component<EntityStore> {
    // These saved fields only undo effects left behind by a crash; they never resume a delivery.
    // Keep the old Goblin-named codec keys so existing player saves can still be cleaned up.
    public static final BuilderCodec<DeliveryAssignmentState> CODEC = BuilderCodec.builder(
        DeliveryAssignmentState.class, DeliveryAssignmentState::new)
        .append(
        new KeyedCodec<>("Owner", Codec.UUID_STRING),
        (state, ownerId) -> state.ownerId = ownerId,
        state -> state.ownerId
    ).add()
        .append(
        new KeyedCodec<>("Assignment", Codec.UUID_STRING),
        (state, assignmentId) -> state.assignmentId = assignmentId,
        state -> state.assignmentId
    ).add()
        .append(
        new KeyedCodec<>("Effects", Codec.STRING_ARRAY),
        (state, effectNames) -> state.option = new DeliveryOption(Arrays.stream(effectNames)
        .map(DeliveryEffect::valueOf)
        .collect(Collectors.toSet())),
        state -> state.effects().stream().map(Enum::name).toArray(String[]::new)
    ).add()
        .append(
        new KeyedCodec<>("DashBound", Codec.BOOLEAN),
        (state, dashBound) -> state.dashBound = dashBound,
        state -> state.dashBound
    ).add()
        .append(
        new KeyedCodec<>("PreviousSecondary", Codec.STRING),
        (state, interactionId) -> state.previousSecondaryInteractionId = interactionId,
        state -> state.previousSecondaryInteractionId
    ).add()
        .append(new KeyedCodec<>("PreviousGoblinModel", Model.ModelReference.CODEC),
        (state, model) -> state.previousModel = model, state -> state.previousModel).add()
        .append(new KeyedCodec<>("PreviousGoblinScale", Codec.FLOAT),
        (state, scale) -> state.previousScale = scale, state -> state.previousScale).add()
        .append(new KeyedCodec<>("OwnedTransformationScale", Codec.FLOAT),
        (state, scale) -> state.ownedTransformationScale = scale, state -> state.ownedTransformationScale).add()
        .append(new KeyedCodec<>("FormSizeApplied", Codec.BOOLEAN),
        (state, applied) -> state.formSizeApplied = applied, state -> state.formSizeApplied).add()
        .append(new KeyedCodec<>("FormBaseScale", Codec.FLOAT),
        (state, scale) -> state.formBaseScale = scale, state -> state.formBaseScale).add()
        .build();

    private UUID ownerId;
    private UUID assignmentId;
    private DeliveryOption option;
    // Derived values are cached because effect checks run every game tick.
    private DeliveryIdentity cachedIdentity;
    private String transformationEffectId;
    private String transformationModelId;
    // These fields let cleanup undo only this mod's changes and leave other plugins' changes alone.
    public boolean effectConflict;
    public int transformationWaitTicks;
    public boolean dashBound;
    public String previousSecondaryInteractionId;
    public Ref<EntityStore> weatherOverrideOwner;
    public Model.ModelReference previousModel;
    public float previousScale = 1f;
    public Float ownedTransformationScale;
    public boolean formSizeApplied;
    public float formBaseScale = 1f;

    /** Lets Hytale create an empty component before loading its saved values. */
    public DeliveryAssignmentState() {
    }

    public DeliveryAssignmentState(DeliveryIdentity identity, DeliveryOption option) {
        this.ownerId = identity.ownerId();
        this.assignmentId = identity.assignmentId();
        this.option = option;
    }

    public DeliveryIdentity identity() {
        if (ownerId == null || assignmentId == null) {
            return null;
        }
        if (cachedIdentity == null || !ownerId.equals(cachedIdentity.ownerId())
            || !assignmentId.equals(cachedIdentity.assignmentId())) {
            cachedIdentity = new DeliveryIdentity(ownerId, assignmentId);
            transformationEffectId = null;
            transformationModelId = null;
        }
        return cachedIdentity;
    }

    public boolean has(DeliveryEffect effect) {
        return option != null && option.effects().contains(effect);
    }

    public Set<DeliveryEffect> effects() {
        return option == null ? Set.of() : option.effects();
    }

    public String entityEffectId(DeliveryEffect effect) {
        if (!effect.isTransformation()) {
            return effect.entityEffectId();
        }
        DeliveryIdentity identity = identity();
        if (transformationEffectId == null) {
            transformationEffectId = effect.entityEffectId(identity);
        }
        return transformationEffectId;
    }

    public String transformationModelId() {
        if (!has(DeliveryEffect.GOBLIN) && !has(DeliveryEffect.KWEEBEC)) {
            return null;
        }
        String effect = entityEffectId(has(DeliveryEffect.GOBLIN) ? DeliveryEffect.GOBLIN : DeliveryEffect.KWEEBEC);
        if (transformationModelId == null) {
            transformationModelId = effect.substring("Parcel_Effect_".length());
        }
        return transformationModelId;
    }

    /** Carries over the original player settings when an updated assignment keeps the same effects. */
    public void inheritRestoration(DeliveryAssignmentState previous) {
        if ((has(DeliveryEffect.GOBLIN) || has(DeliveryEffect.KWEEBEC))
            && (previous.has(DeliveryEffect.GOBLIN) || previous.has(DeliveryEffect.KWEEBEC))) {
            previousModel = previous.previousModel;
            previousScale = previous.previousScale;
            ownedTransformationScale = previous.ownedTransformationScale;
        }
        if ((has(DeliveryEffect.BIG) && previous.has(DeliveryEffect.BIG))
            || (has(DeliveryEffect.SMALL) && previous.has(DeliveryEffect.SMALL))) {
            if ((has(DeliveryEffect.GOBLIN) && previous.has(DeliveryEffect.GOBLIN))
                || (has(DeliveryEffect.KWEEBEC) && previous.has(DeliveryEffect.KWEEBEC))) {
                formSizeApplied = previous.formSizeApplied;
                formBaseScale = previous.formBaseScale;
            }
        }
        if (has(DeliveryEffect.DASH_ONLY) && previous.has(DeliveryEffect.DASH_ONLY)) {
            dashBound = previous.dashBound;
            previousSecondaryInteractionId = previous.previousSecondaryInteractionId;
        }
        if (has(DeliveryEffect.BLINDNESS) && previous.has(DeliveryEffect.BLINDNESS)) {
            weatherOverrideOwner = previous.weatherOverrideOwner;
        }
    }

    @Override
    public DeliveryAssignmentState clone() {
        DeliveryAssignmentState copy = new DeliveryAssignmentState();
        copy.ownerId = ownerId;
        copy.assignmentId = assignmentId;
        copy.option = option;
        copy.effectConflict = effectConflict;
        copy.transformationWaitTicks = transformationWaitTicks;
        copy.dashBound = dashBound;
        copy.previousSecondaryInteractionId = previousSecondaryInteractionId;
        copy.weatherOverrideOwner = weatherOverrideOwner;
        copy.previousModel = previousModel;
        copy.previousScale = previousScale;
        copy.ownedTransformationScale = ownedTransformationScale;
        copy.formSizeApplied = formSizeApplied;
        copy.formBaseScale = formBaseScale;
        return copy;
    }
}
