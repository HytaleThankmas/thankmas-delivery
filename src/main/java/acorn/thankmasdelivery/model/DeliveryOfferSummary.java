package acorn.thankmasdelivery.model;

import acorn.thankmasdelivery.effect.DeliveryEffect;

public record DeliveryOfferSummary(int number, int points, int positive, int neutral, int negative) {
    public static DeliveryOfferSummary of(int number, DeliveryOption option) {
        int positive = 0, neutral = 0, negative = 0;
        for (DeliveryEffect effect : option.effects()) {
            switch (effect.category()) {
                case POSITIVE -> positive++;
                case NEUTRAL -> neutral++;
                case NEGATIVE -> negative++;
            }
        }
        return new DeliveryOfferSummary(number, option.points(), positive, neutral, negative);
    }
}
