package acorn.thankmasdelivery.page;

import acorn.thankmasdelivery.model.DeliveryOfferSummary;
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
import java.util.List;

public class DeliveryPage extends InteractiveCustomUIPage<DeliveryPage.DeliveryPageData> {

    public static final String UI_PAGE_PATH = "Delivery/DeliveryPage.ui";
    public static final String UI_SLOT_PATH = "Delivery/GiftSlots.ui";

    private final DeliveryService deliveryService;
    private final List<DeliveryOfferSummary> offers;

    public DeliveryPage(@Nonnull PlayerRef playerRef, @Nonnull DeliveryService deliveryService, List<DeliveryOfferSummary> offers) {
        super(playerRef, CustomPageLifetime.CanDismiss, DeliveryPageData.CODEC);
        this.deliveryService = deliveryService;
        this.offers = offers != null && !offers.isEmpty() ? offers : deliveryService.getOrCreateOfferSummaries(playerRef);
    }

    public DeliveryPage(@Nonnull PlayerRef playerRef, @Nonnull DeliveryService deliveryService) {
        this(playerRef, deliveryService, null);
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(UI_PAGE_PATH);
        commandBuilder.clear("#GiftGrid");

        for (int i = 0; i < offers.size() && i < 3; i++) {
            DeliveryOfferSummary summary = offers.get(i);
            commandBuilder.append("#GiftGrid", UI_SLOT_PATH);
            String slotPath = "#GiftGrid[" + i + "]";

            String parcelTitle = switch (i) {
                case 0 -> "Parcel One";
                case 1 -> "Parcel Two";
                case 2 -> "Parcel Three";
                default -> "Parcel " + (i + 1);
            };

            commandBuilder.set(slotPath + " #ParcelTitle.Text", parcelTitle);
            commandBuilder.set(slotPath + " #PointsTitle.Text", "Points: " + summary.points());
            commandBuilder.set(slotPath + " #ParcelImage.ItemId", DeliveryService.PRESENT_BLOCK_ID);
            commandBuilder.set(slotPath + " #PositiveText.Text", "Positive: " + summary.positive());
            commandBuilder.set(slotPath + " #NeutralText.Text", "Neutral: " + summary.neutral());
            commandBuilder.set(slotPath + " #PositiveText2.Text", "Negative: " + summary.negative());

            EventData clickData = EventData.of(DeliveryPageData.KEY_OPTION, String.valueOf(i + 1));
            eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, slotPath + " #ChooseButton", clickData, false);
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull DeliveryPageData data
    ) {
        super.handleDataEvent(ref, store, data);
        String optionStr = data.getOption();
        if (optionStr == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());


        try {
            int optionNumber = Integer.parseInt(optionStr);
            if (optionNumber >= 1 && optionNumber <= 3) {
                deliveryService.choose(store, ref, playerRef, optionNumber);
                if (deliveryService.hasActiveDelivery(playerRef.getUuid())) {
                    deliveryService.openActiveDeliveryPage(store, ref, playerRef);
                } else if (player != null) {
                    player.getPageManager().closePage(ref, store);
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }

    public static final class DeliveryPageData {
        public static final String KEY_OPTION = "Option";

        private String option;

        public String getOption() {
            return option;
        }

        public void setOption(String option) {
            this.option = option;
        }

        public static final BuilderCodec<DeliveryPageData> CODEC = BuilderCodec.builder(DeliveryPageData.class, DeliveryPageData::new)
            .append(new KeyedCodec<>(KEY_OPTION, Codec.STRING), DeliveryPageData::setOption, DeliveryPageData::getOption)
            .add()
            .build();
    }
}
