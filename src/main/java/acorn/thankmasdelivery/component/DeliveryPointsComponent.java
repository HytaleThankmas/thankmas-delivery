package acorn.thankmasdelivery.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class DeliveryPointsComponent implements Component<EntityStore> {

    private static ComponentType<EntityStore, DeliveryPointsComponent> componentType;

    public static ComponentType<EntityStore, DeliveryPointsComponent> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, DeliveryPointsComponent> type) {
        componentType = type;
    }

    public static final BuilderCodec<DeliveryPointsComponent> CODEC = BuilderCodec.builder(
            DeliveryPointsComponent.class, DeliveryPointsComponent::new)
        .append(
            new KeyedCodec<>("Points", Codec.INTEGER),
            (comp, pts) -> comp.points = pts,
            comp -> comp.points
        ).add()
        .build();

    private int points = 0;

    public DeliveryPointsComponent() {
    }

    public DeliveryPointsComponent(int points) {
        this.points = Math.max(0, points);
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = Math.max(0, points);
    }

    public boolean addPoints(int amount) {
        if (amount <= 0) {
            return false;
        }
        try {
            this.points = Math.addExact(this.points, amount);
        } catch (ArithmeticException overflow) {
            this.points = Integer.MAX_VALUE;
        }
        return true;
    }

    public boolean removePoints(int amount) {
        if (amount <= 0 || this.points < amount) {
            return false;
        }
        this.points -= amount;
        return true;
    }

    public boolean hasPoints(int amount) {
        return amount >= 0 && this.points >= amount;
    }

    @Nonnull
    @Override
    public DeliveryPointsComponent clone() {
        return new DeliveryPointsComponent(this.points);
    }

    public static void ensurePoints(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor, int initialPoints) {
        if (componentType != null && accessor.getComponent(ref, componentType) == null) {
            accessor.putComponent(ref, componentType, new DeliveryPointsComponent(initialPoints));
        }
    }

    public static int getPoints(Ref<EntityStore> ref, ComponentAccessor<EntityStore> accessor) {
        if (componentType == null) return 0;
        DeliveryPointsComponent comp = accessor.getComponent(ref, componentType);
        return comp != null ? comp.getPoints() : 0;
    }
}
