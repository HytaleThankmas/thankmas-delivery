package acorn.thankmasdelivery.util;

import acorn.thankmasdelivery.effect.DeliveryEffect;
import acorn.thankmasdelivery.effect.DeliveryEffectCategory;
import acorn.thankmasdelivery.model.DeliveryOfferSummary;
import acorn.thankmasdelivery.model.DeliveryOption;

import com.hypixel.hytale.server.core.Message;

import java.util.List;

public final class DeliveryMessages {
    private static final String HEADING_COLOR = "#F6C85F";
    private static final String COMMAND_COLOR = "#75D5FF";
    private static final String DESCRIPTION_COLOR = "#B8C3D1";
    private static final String POSITIVE_COLOR = "#7DDE92";
    private static final String NEUTRAL_COLOR = "#F6C85F";
    private static final String NEGATIVE_COLOR = "#FF8A8A";

    private DeliveryMessages() {
    }

    public static Message withPrefix(Message message) {
        return Message.join(
            Message.raw("[Thankmas Delivery] ").color(HEADING_COLOR).bold(true),
            message
        );
    }

    public static Message offerSummaries(List<DeliveryOfferSummary> options) {
        Message message = Message.empty()
            .insert(Message.raw("Choose your parcel\n").color(HEADING_COLOR).bold(true));
        for (DeliveryOfferSummary option : options) {
            message.insert(Message.raw("\nParcel " + option.number() + " | " + option.points() + " points\n").color(COMMAND_COLOR))
                .insert(Message.raw("Positive: " + option.positive() + "  ").color(POSITIVE_COLOR))
                .insert(Message.raw("Neutral: " + option.neutral() + "  ").color(NEUTRAL_COLOR))
                .insert(Message.raw("Negative: " + option.negative() + "\n").color(NEGATIVE_COLOR));
        }
        return message.insert(Message.raw("\n use /parcel choose (1-3). Effects are revealed after selection.").color(DESCRIPTION_COLOR));
    }

    public static Message effectList() {
        Message message = Message.empty()
            .insert(Message.raw("Available parcel effects").color(HEADING_COLOR).bold(true));
        for (DeliveryEffectCategory category : DeliveryEffectCategory.values()) {
            String color = categoryColor(category);
            message.insert(Message.raw("\n\n" + category.displayName() + " effects").color(color).bold(true));
            for (DeliveryEffect effect : DeliveryEffect.values()) {
                if (effect.category() != category) {
                    continue;
                }
                message.insert(Message.raw("\n  " + effect.displayName()).color(color).bold(true))
                    .insert(Message.raw(" - " + effect.description()).color(DESCRIPTION_COLOR));
            }
        }
        return message;
    }

    public static List<Message> assignment(DeliveryOption option, String recipientName, String hint) {
        Message effects = Message.raw("Your Effects are:");
        for (DeliveryEffectCategory category : DeliveryEffectCategory.values()) {
            for (DeliveryEffect effect : DeliveryEffect.values()) {
                if (effect.category() == category && option.effects().contains(effect)) {
                    effects.insert(Message.raw("\n- " + effect.displayName()).color(categoryColor(category)));
                }
            }
        }
        return List.of(effects, Message.raw(destinationText(recipientName, hint)));
    }

    public static String destinationText(String recipientName, String hint) {
        String location = hint == null || hint.isBlank() ? "in an Unknown Location" : hint.strip();
        return "Delivery to " + recipientName + " who can be found " + location;
    }

    private static String categoryColor(DeliveryEffectCategory category) {
        return switch (category) {
            case POSITIVE -> POSITIVE_COLOR;
            case NEUTRAL -> NEUTRAL_COLOR;
            case NEGATIVE -> NEGATIVE_COLOR;
        };
    }
}
