package acorn.thankmasdelivery.component;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Random;

/** Saves an entity's delivery role, ID, and display name. */
public final class DeliveryNpc implements Component<EntityStore> {
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final Random RANDOM = new Random();

    private static ComponentType<EntityStore, DeliveryNpc> componentType;

    public static ComponentType<EntityStore, DeliveryNpc> getComponentType() {
        return componentType;
    }

    public static void setComponentType(ComponentType<EntityStore, DeliveryNpc> type) {
        componentType = type;
    }

    public static final BuilderCodec<DeliveryNpc> CODEC = BuilderCodec.builder(DeliveryNpc.class, DeliveryNpc::new)
            .append(new KeyedCodec<>("Kind", Codec.STRING),
                    (npc, kind) -> npc.kind = kind, npc -> npc.kind).add()
            .append(new KeyedCodec<>("Id", Codec.STRING),
                    (npc, id) -> npc.id = id, npc -> npc.id).add()
            .append(new KeyedCodec<>("Name", Codec.STRING),
                    (npc, name) -> npc.name = name, npc -> npc.name).add()
            .append(new KeyedCodec<>("BonusPoints", Codec.INTEGER),
                    (npc, bonusPoints) -> npc.bonusPoints = bonusPoints, npc -> npc.bonusPoints).add()
            .append(new KeyedCodec<>("Hint", Codec.STRING),
                    (npc, hint) -> npc.hint = hint, npc -> npc.hint).add()
            .build();


    private String kind = "dispatcher";
    private String id = "thankmas";
    private String name = "Thankmas Dispatcher";
    private int bonusPoints = 0;
    private String hint = "";

    public DeliveryNpc() {
    }

    public DeliveryNpc(String kind, String id, String name) {
        this(kind, id, name, 0, "");
    }

    public DeliveryNpc(String kind, String id, String name, int bonusPoints) {
        this(kind, id, name, bonusPoints, "");
    }

    public DeliveryNpc(String kind, String id, String name, int bonusPoints, String hint) {
        this.kind = (kind == null || kind.isBlank()) ? "dispatcher" : kind;
        this.name = (name == null || name.isBlank()) ? "Thankmas Dispatcher" : name;
        this.id = (id == null || id.isBlank()) ? generateId(this.name) : id;
        this.bonusPoints = bonusPoints;
        this.hint = (hint == null) ? "" : hint;
    }

    public String id() {
        return id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = (id == null || id.isBlank()) ? generateId(this.name) : id;
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String kind() {
        return kind;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public boolean dispatcher() {
        return "dispatcher".equalsIgnoreCase(kind);
    }

    public boolean recipient() {
        return "recipient".equalsIgnoreCase(kind);
    }

    public String hint() {
        return hint;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = (hint == null) ? "" : hint;
    }

    @Override
    public DeliveryNpc clone() {
        return new DeliveryNpc(kind, id, name, bonusPoints, hint);
    }

    public int getBonusPoints() {
        return bonusPoints;
    }

    public void setBonusPoints(int bonusPoints) {
        this.bonusPoints = bonusPoints;
    }

    public static String generateId(String name) {
        if (name != null) {
            String cleaned = name.toLowerCase().replaceAll("\\s+", "").replaceAll("[^a-z0-9_-]", "");
            if (!cleaned.isEmpty()) {
                return cleaned.length() > 64 ? cleaned.substring(0, 64) : cleaned;
            }
        }
        StringBuilder sb = new StringBuilder("npc_");
        for (int i = 0; i < 8; i++) {
            sb.append(ID_CHARS.charAt(RANDOM.nextInt(ID_CHARS.length())));
        }
        return sb.toString();
    }

}
