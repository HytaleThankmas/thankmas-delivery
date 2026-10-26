package acorn.thankmasdelivery.npc;

/** Chooses a dispatcher greeting based on the player's current and past deliveries. */
public final class DeliveryDialogue {
    public enum Outcome { NONE, COMPLETED, ABANDONED }

    private DeliveryDialogue() { }

    public static String greeting(boolean talked, Outcome lastOutcome, boolean active) {
        if (active) {
            return "What are you doing, go deliver that present!";
        }
        if (!talked) {
            return "Oh My Gaia, Avatar please we need help delivering these presents! Its Thankmas, we can't have our residents not get presents! Will you help us?";
        }
        return switch (lastOutcome) {
            case COMPLETED -> "You're back! Good work out there, are you ready to deliver another?";
            case ABANDONED -> "Welcome back! Too bad about that last delivery, luckily their gift eventually found its way to the recipient? Would you like to try a different delivery?";
            case NONE -> "Hey! Will you help us deliver presents?";
        };
    }
}
