package acorn.thankmasdelivery.component;

import acorn.thankmasdelivery.model.DeliveryIdentity;

import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.modules.interaction.components.CarriedBlock;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/** Links a dropped or thrown parcel to its owner, delivery, and current throw. */
public final class ThrownPresent implements Component<EntityStore> {
    private final CarriedBlock block;
    private final DeliveryIdentity identity;
    private final UUID dropId;

    public ThrownPresent() {
        block = new CarriedBlock();
        identity = null;
        dropId = null;
    }

    public ThrownPresent(CarriedBlock block, DeliveryIdentity identity, UUID dropId) {
        this.block = (CarriedBlock) block.clone();
        this.identity = identity;
        this.dropId = dropId;
    }

    public DeliveryIdentity identity() {
        return identity;
    }

    public UUID dropId() {
        return dropId;
    }

    public CarriedBlock copyBlock() {
        return (CarriedBlock) block.clone();
    }

    @Override
    public ThrownPresent clone() {
        return new ThrownPresent(block, identity, dropId);
    }
}
