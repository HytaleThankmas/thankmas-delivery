package acorn.thankmasdelivery.page;

import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.effect.DeliveryEffectCategory;
import acorn.thankmasdelivery.model.DeliveryOption;
import acorn.thankmasdelivery.npc.DeliveryNpcAppearanceHelper;
import acorn.thankmasdelivery.persistence.DeliveryStore;
import acorn.thankmasdelivery.service.DeliveryService;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

public class ActiveDeliveryPage extends InteractiveCustomUIPage<ActiveDeliveryPage.ActiveDeliveryPageData> {

    public static final String UI_PAGE_PATH = "Delivery/ActiveDeliveryPage.ui";
    public static final String UI_SLOT_PATH = "Delivery/ChosenGiftSlots.ui";

    private final DeliveryService deliveryService;
    private final DeliveryStore.Run run;
    private final DeliveryStore.Destination explicitDestination;

    public ActiveDeliveryPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull DeliveryService deliveryService,
        @Nullable DeliveryStore.Run run,
        @Nullable DeliveryStore.Destination explicitDestination
    ) {
        super(playerRef, CustomPageLifetime.CanDismiss, ActiveDeliveryPageData.CODEC);
        this.deliveryService = deliveryService;
        this.run = run != null ? run : fetchActiveRun(playerRef, deliveryService);
        this.explicitDestination = explicitDestination;
    }

    public ActiveDeliveryPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull DeliveryService deliveryService,
        @Nullable DeliveryStore.Run run
    ) {
        this(playerRef, deliveryService, run, null);
    }

    public ActiveDeliveryPage(@Nonnull PlayerRef playerRef, @Nonnull DeliveryService deliveryService) {
        this(playerRef, deliveryService, null, null);
    }

    private static DeliveryStore.Run fetchActiveRun(PlayerRef playerRef, DeliveryService deliveryService) {
        try {
            DeliveryStore.Profile profile = deliveryService.deliveryStore().profile(playerRef.getUuid(), playerRef.getUsername());
            return profile != null ? profile.active() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(UI_PAGE_PATH);
        commandBuilder.clear("#ChosenGiftSlotWrapper");
        commandBuilder.append("#ChosenGiftSlotWrapper", UI_SLOT_PATH);

        String parcelTitle;
        int points;
        String positiveText;
        String neutralText;
        String negativeText;
        String recipientName;
        String recipientHint;
        String recipientImagePath;

        if (run != null) {
            DeliveryOption option = run.option();
            parcelTitle = "Active Parcel";
            points = option != null ? option.points() : 0;
            Set<DeliveryEffect> effects = option != null ? option.effects() : Set.of();
            positiveText = formatEffects("Positive", effects, DeliveryEffectCategory.POSITIVE);
            neutralText = formatEffects("Neutral", effects, DeliveryEffectCategory.NEUTRAL);
            negativeText = formatEffects("Negative", effects, DeliveryEffectCategory.NEGATIVE);
        }
        else return;

        DeliveryStore.Destination destination = explicitDestination != null
            ? explicitDestination
            : (run != null ? run.recipient() : null);

        if (destination != null) {
            recipientName = destination.name() != null ? destination.name() : "Recipient";
            recipientHint = destination.hint() != null && !destination.hint().isBlank()
                ? destination.hint()
                : "Deliver the parcel to complete your assignment.";
            recipientImagePath = DeliveryNpcAppearanceHelper.resolveNpcAssetPath(destination);
        } else {
            return;
        }

        commandBuilder.set("#ChosenGiftSlotWrapper #ParcelTitle.Text", parcelTitle);
        commandBuilder.set("#ChosenGiftSlotWrapper #PointsTitle.Text", "Points: " + points);
        commandBuilder.set("#ChosenGiftSlotWrapper #ParcelImage.ItemId", DeliveryService.PRESENT_BLOCK_ID);
        commandBuilder.set("#ChosenGiftSlotWrapper #PositiveText.Text", positiveText);
        commandBuilder.set("#ChosenGiftSlotWrapper #NeutralText.Text", neutralText);
        commandBuilder.set("#ChosenGiftSlotWrapper #NegativeText.Text", negativeText);

        commandBuilder.set("#RecipientName.Text", recipientName);
        commandBuilder.set("#RecipientHint.Text", recipientHint);
        commandBuilder.set("#RecipientImage.AssetPath", recipientImagePath);
    }

    public static String formatEffects(String categoryHeader, Set<DeliveryEffect> effects, DeliveryEffectCategory category) {
        StringBuilder sb = new StringBuilder(categoryHeader).append(":");
        List<DeliveryEffect> matched = effects.stream()
            .filter(e -> e.category() == category)
            .toList();
        if (matched.isEmpty()) {
            sb.append("\n- None");
        } else {
            for (DeliveryEffect e : matched) {
                sb.append("\n- ").append(e.displayName());
            }
        }
        return sb.toString();
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull ActiveDeliveryPageData data
    ) {
        super.handleDataEvent(ref, store, data);
    }

    public static final class ActiveDeliveryPageData {
        public static final String KEY_ACTION = "Action";

        private String action;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public static final BuilderCodec<ActiveDeliveryPageData> CODEC = BuilderCodec.builder(ActiveDeliveryPageData.class, ActiveDeliveryPageData::new)
            .append(new KeyedCodec<>(KEY_ACTION, Codec.STRING), ActiveDeliveryPageData::setAction, ActiveDeliveryPageData::getAction)
            .add()
            .build();
    }
}
