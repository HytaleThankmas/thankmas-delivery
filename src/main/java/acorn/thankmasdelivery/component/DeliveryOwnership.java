package acorn.thankmasdelivery.component;

import acorn.thankmasdelivery.model.DeliveryIdentity;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import java.util.UUID;

public final class DeliveryOwnership implements Component<ChunkStore> {
    public static final BuilderCodec<DeliveryOwnership> CODEC = BuilderCodec.builder(
        DeliveryOwnership.class, DeliveryOwnership::new)
        .append(
        new KeyedCodec<>("Owner", Codec.UUID_STRING),
        (ownership, ownerId) -> ownership.ownerId = ownerId,
        ownership -> ownership.ownerId
    ).add()
        .append(
        new KeyedCodec<>("Assignment", Codec.UUID_STRING),
        (ownership, assignmentId) -> ownership.assignmentId = assignmentId,
        ownership -> ownership.assignmentId
    ).add()
        .build();

    private UUID ownerId;
    private UUID assignmentId;

    public DeliveryOwnership() {
    }

    public DeliveryOwnership(DeliveryIdentity identity) {
        ownerId = identity.ownerId();
        assignmentId = identity.assignmentId();
    }

    public DeliveryIdentity identity() {
        return ownerId == null || assignmentId == null ? null : new DeliveryIdentity(ownerId, assignmentId);
    }

    @Override
    public DeliveryOwnership clone() {
        DeliveryOwnership copy = new DeliveryOwnership();
        copy.ownerId = ownerId;
        copy.assignmentId = assignmentId;
        return copy;
    }
}
