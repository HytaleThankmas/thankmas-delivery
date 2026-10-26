package acorn.thankmasdelivery.model;

import java.util.Objects;
import java.util.UUID;

/** Identifies one delivery by its owner ID and assignment ID. */
public record DeliveryIdentity(UUID ownerId, UUID assignmentId) {
    public DeliveryIdentity {
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(assignmentId);
    }
}
