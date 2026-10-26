package acorn.thankmasdelivery.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/** Saves an entity's delivery role, ID, and display name. */
public final class DeliveryNpc implements Component<EntityStore> {
    public static final BuilderCodec<DeliveryNpc> CODEC = BuilderCodec.builder(DeliveryNpc.class, DeliveryNpc::new)
        .append(new KeyedCodec<>("Kind", Codec.STRING),
        (npc, kind) -> npc.kind = kind, npc -> npc.kind).add()
        .append(new KeyedCodec<>("Id", Codec.STRING),
        (npc, id) -> npc.id = id, npc -> npc.id).add()
        .append(new KeyedCodec<>("Name", Codec.STRING),
        (npc, name) -> npc.name = name, npc -> npc.name).add()
        .build();

    private String kind = "dispatcher";
    private String id = "thankmas";
    private String name = "Thankmas Dispatcher";

    public DeliveryNpc() { }

    public DeliveryNpc(String kind, String id, String name) {
        this.kind = kind;
        this.id = id;
        this.name = name;
    }

    public String id() {
        return id;
    }
    public String name() {
        return name;
    }
    public boolean dispatcher() {
        return "dispatcher".equals(kind);
    }
    public boolean recipient() {
        return "recipient".equals(kind);
    }

    @Override
    public DeliveryNpc clone() {
        return new DeliveryNpc(kind, id, name);
    }
}
