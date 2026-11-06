package acorn.thankmasdelivery.model;

import acorn.thankmasdelivery.effect.DeliveryEffect;

public record DeliveryOfferSummary(int number, int points, int positive, int neutral, int negative, int bonusPoints, String recipientName) {
    public DeliveryOfferSummary(int number, int points, int positive, int neutral, int negative, int bonusPoints) {
        this(number, points, positive, neutral, negative, bonusPoints, "");
    }

    public DeliveryOfferSummary(int number, int points, int positive, int neutral, int negative) {
        this(number, points, positive, neutral, negative, 0, "");
    }

    public static DeliveryOfferSummary of(int number, DeliveryOption option) {
        return of(number, option, 0, "");
    }

    public static DeliveryOfferSummary of(int number, DeliveryOption option, int bonusPoints) {
        return of(number, option, bonusPoints, "");
    }

    public static DeliveryOfferSummary of(int number, DeliveryOption option, int bonusPoints, String recipientName) {
        int positive = 0, neutral = 0, negative = 0;
        for (DeliveryEffect effect : option.effects()) {
            switch (effect.category()) {
                case POSITIVE -> positive++;
                case NEUTRAL -> neutral++;
                case NEGATIVE -> negative++;
            }
        }
        return new DeliveryOfferSummary(number, option.points(), positive, neutral, negative, bonusPoints, recipientName != null ? recipientName : "");
    }
}
