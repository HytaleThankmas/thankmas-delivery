package acorn.thankmasdelivery.command;

import acorn.thankmasdelivery.component.DeliveryNpc;
import acorn.thankmasdelivery.npc.DeliveryCelebrationEmotes;
import acorn.thankmasdelivery.persistence.DeliveryStore;
import acorn.thankmasdelivery.persistence.DeliveryStore.Destination;
import acorn.thankmasdelivery.persistence.DeliveryStore.Profile;
import acorn.thankmasdelivery.service.DeliveryService;
import acorn.thankmasdelivery.util.DeliveryMessages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.EmoteAsset;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.collision.CollisionMath;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Interactable;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.npc.role.support.DisplayNameSupport;
import org.joml.Vector2d;
import org.joml.Vector3d;

import java.util.List;

public final class DeliveryNpcCommand extends AbstractPlayerCommand {
    private static final float TARGET_RANGE = 5;

    private static Ref<EntityStore> target(CommandContext context, Store<EntityStore> store, Ref<EntityStore> player) {
        Ref<EntityStore> entity = TargetUtil.getTargetEntity(player, TARGET_RANGE, store);
        if (entity != null && entity.isValid()) {
            TransformComponent transform = store.getComponent(entity, TransformComponent.getComponentType());
            BoundingBox box = store.getComponent(entity, BoundingBox.getComponentType());
            Transform look = TargetUtil.getLook(player, store);
            Vector2d hit = new Vector2d();
            if (transform != null && box != null && CollisionMath.intersectRayAABB(look.getPosition(), look.getDirection(),
                transform.getPosition().x, transform.getPosition().y, transform.getPosition().z, box.getBoundingBox(), hit)) {
                // Check for walls only up to the front of the entity, so walls behind it do not block selection.
                if (TargetUtil.getTargetBlock(player, Math.max(0, hit.x), store) == null) {
                    return entity;
                }
            }
        }
        context.sendMessage(Message.raw("No NPC or Entity found in direct line of sight"));
        return null;
    }

    public DeliveryNpcCommand(DeliveryService service) {
        super("npc", "Configure or remove Thankmas parcel NPCs.");
        requirePermission("thankmasdelivery.admin");
        addSubCommand(new Configure(service, false));
        addSubCommand(new Configure(service, true));
        addSubCommand(new Remove(service));
        addSubCommand(new Location(service));
        addSubCommand(new Emote(service));
        addSubCommand(new Manage(service, "assignments"));
        addSubCommand(new Manage(service, "retire"));
    }
    @Override
    protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
        context.sendMessage(Message.raw("/parcel npc dispatcher <id> <name> | /parcel npc recipient <id> <name> | /parcel npc location <id> <hint> | /parcel npc emote <id> <tag|random> | /parcel npc remove (look at NPC) | assignments/retire <id>"));
    }

    private static final class Configure extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final boolean recipient;
        private final RequiredArg<String> id, name;
        Configure(DeliveryService service, boolean recipient) {
            super(recipient ? "recipient" : "dispatcher", "Apply a parcel role to the entity you are looking at.");
            requirePermission("thankmasdelivery.admin");
            this.service = service;
            this.recipient = recipient;
            id = withRequiredArg("id", "Lowercase NPC ID", ArgTypes.STRING);
            name = withRequiredArg("name", "NPC display name", ArgTypes.GREEDY_STRING);
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            Ref<EntityStore> npc = target(context, store, ref);
            if (npc == null) {
                return;
            }
            if (store.getComponent(npc, service.npcType()) != null) {
                context.sendMessage(Message.raw("That entity is already configured as a parcel NPC."));
                return;
            }
            String recipientId = context.get(id);
            String displayName = context.get(name);
            if (!recipientId.matches("[a-z0-9_-]{1,64}") || displayName.isBlank() || displayName.length() > 80) {
                context.sendMessage(Message.raw("Use a lowercase ID (letters, numbers, - or _) and a name up to 80 characters."));
                return;
            }
            if (recipient && service.deliveryStore().recipient(recipientId) != null) {
                context.sendMessage(Message.raw("That recipient ID is already registered. Remove the old recipient first."));
                return;
            }
            if (recipient && !service.reserveRecipient(recipientId)) {
                context.sendMessage(Message.raw("A change to this recipient is already pending."));
                return;
            }
            TransformComponent transform = store.getComponent(npc, TransformComponent.getComponentType());
            UUIDComponent uuid = store.getComponent(npc, UUIDComponent.getComponentType());
            if (transform == null || uuid == null) {
                if (recipient) {
                    service.releaseRecipient(recipientId);
                }
                context.sendMessage(Message.raw("That entity has no persistent identity or position."));
                return;
            }
            Nameplate nameplate = store.getComponent(npc, Nameplate.getComponentType());
            Nameplate oldNameplate = nameplate == null ? null : (Nameplate) nameplate.clone();
            DisplayNameComponent oldDisplayName = store.getComponent(npc, DisplayNameComponent.getComponentType());
            PersistentDisplayName oldPersistentName = store.getComponent(npc, PersistentDisplayName.getComponentType());
            Interactions oldInteractions = store.getComponent(npc, Interactions.getComponentType());
            boolean hadInteractable = store.getComponent(npc, Interactable.getComponentType()) != null;
            DeliveryNpc marker = new DeliveryNpc(recipient ? "recipient" : "dispatcher", recipientId, displayName);
            Runnable rollback = () -> {
                if (!npc.isValid() || store.getComponent(npc, service.npcType()) != marker) {
                    return;
                }
                store.removeComponent(npc, service.npcType());
                if (oldNameplate == null) {
                    store.tryRemoveComponent(npc, Nameplate.getComponentType());
                } else {
                    store.putComponent(npc, Nameplate.getComponentType(), oldNameplate);
                }
                if (oldDisplayName == null) {
                    store.tryRemoveComponent(npc, DisplayNameComponent.getComponentType());
                } else {
                    store.putComponent(npc, DisplayNameComponent.getComponentType(), oldDisplayName);
                }
                if (oldPersistentName == null) {
                    store.tryRemoveComponent(npc, PersistentDisplayName.getComponentType());
                } else {
                    store.putComponent(npc, PersistentDisplayName.getComponentType(), oldPersistentName);
                }
                if (oldInteractions == null) {
                    store.tryRemoveComponent(npc, Interactions.getComponentType());
                } else {
                    store.putComponent(npc, Interactions.getComponentType(), oldInteractions);
                }
                if (!hadInteractable) {
                    store.tryRemoveComponent(npc, Interactable.getComponentType());
                }
            };
            try {
                store.addComponent(npc, service.npcType(), marker);
                DisplayNameSupport.setDisplayName(npc, displayName, store);
                Interactions interactions = oldInteractions == null ? new Interactions() : (Interactions) oldInteractions.clone();
                if (interactions.getInteractionId(InteractionType.Use) == null) {
                    interactions.setInteractionId(InteractionType.Use, "Parcel_Delivery_NPC");
                    interactions.setInteractionHint("server.interactionHints.Parcel_" + (recipient ? "Recipient" : "Dispatcher"));
                }
                store.putComponent(npc, Interactions.getComponentType(), interactions);
                if (!hadInteractable) {
                    store.addComponent(npc, Interactable.getComponentType(), Interactable.INSTANCE);
                }
                if (!recipient) {
                    context.sendMessage(Message.raw("Configured " + displayName + " (" + recipientId + ")."));
                    return;
                }
                Vector3d position = transform.getPosition();
                Destination destination = new DeliveryStore.Destination(recipientId, displayName, world.getName(), position.x, position.y, position.z,
                    "", uuid.getUuid(), world.getWorldConfig().getUuid(), false);
                service.adminAction(recipientId, world, player, () -> {
                    if (service.deliveryStore().recipient(recipientId) != null) {
                        throw new IllegalStateException("That recipient ID is already registered.");
                    }
                    service.deliveryStore().register(destination);
                    return true;
                }, ok -> context.sendMessage(Message.raw("Configured " + displayName + " (" + recipientId + ").")), rollback);
            } catch (RuntimeException error) {
                if (recipient) {
                    service.releaseRecipient(recipientId);
                }
                rollback.run();
                throw error;
            }
        }
    }
    private static final class Location extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final RequiredArg<String> id, hint;
        Location(DeliveryService service) {
            super("location", "Set a recipient's delivery location hint.");
            this.service = service;
            requirePermission("thankmasdelivery.admin");
            id = withRequiredArg("id", "Registered recipient ID", ArgTypes.STRING);
            hint = withRequiredArg("hint", "Location hint (quotes optional)", ArgTypes.GREEDY_STRING);
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            String recipientId = context.get(id), locationHint = context.get(hint);
            service.adminAction(world, player, () -> service.deliveryStore().setLocationHint(recipientId, locationHint), recipient ->
                context.sendMessage(DeliveryMessages.withPrefix(Message.raw(DeliveryMessages.destinationText(recipient.name(), recipient.hint())))), () -> { });
        }
    }

    private static final class Emote extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final RequiredArg<String> id, tag;
        Emote(DeliveryService service) {
            super("emote", "Set a recipient's celebration emote, or random to clear its override.");
            this.service = service;
            requirePermission("thankmasdelivery.admin");
            id = withRequiredArg("id", "Registered recipient ID", ArgTypes.STRING);
            tag = withRequiredArg("tag", "Any registered emote tag, or random", ArgTypes.GREEDY_STRING);
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            final String emote;
            try {
                emote = DeliveryCelebrationEmotes.resolve(context.get(tag), CosmeticsModule.get().getRegistry().getEmotesInGame().keySet(),
                    EmoteAsset.getAssetMap().getAssetMap().keySet());
            } catch (IllegalArgumentException invalid) {
                context.sendMessage(DeliveryMessages.withPrefix(Message.raw(invalid.getMessage())));
                return;
            }
            String recipientId = context.get(id);
            service.adminAction(world, player, () -> service.deliveryStore().setCelebrationEmote(recipientId, emote), recipient ->
                context.sendMessage(DeliveryMessages.withPrefix(Message.raw("Celebration for " + recipient.name() + ": "
                + (recipient.celebrationEmote().isEmpty() ? "random (BlowKiss, Salute, DanceBoogie, DancePop)" : recipient.celebrationEmote()) + "."))), () -> { });
        }
    }

    private static final class Remove extends AbstractPlayerCommand {
        private final DeliveryService service;
        Remove(DeliveryService service) {
            super("remove", "Remove the parcel NPC you are looking at.");
            this.service = service;
            requirePermission("thankmasdelivery.admin");
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            Ref<EntityStore> npc = target(context, store, ref);
            if (npc == null) {
                return;
            }
            DeliveryNpc marker = store.getComponent(npc, service.npcType());
            if (marker == null) {
                context.sendMessage(Message.raw("That entity is not configured as a parcel NPC."));
                return;
            }
            if (marker.recipient()) {
                if (!service.reserveRecipient(marker.id())) {
                    context.sendMessage(Message.raw("A change to this recipient is already pending."));
                    return;
                }
                service.adminAction(marker.id(), world, player, () -> service.deliveryStore().removeRecipient(marker.id()), count -> {
                    if (npc.isValid()) {
                        store.removeEntity(npc, RemoveReason.REMOVE);
                    }
                    context.sendMessage(Message.raw("Removed " + marker.name() + ". Abandoned " + count + " deliveries."));
                }, () -> { });
            } else {
                store.removeEntity(npc, RemoveReason.REMOVE);
                context.sendMessage(Message.raw("Removed " + marker.name() + "."));
            }
        }
    }

    private static final class Manage extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final String action;
        private final RequiredArg<String> id;
        Manage(DeliveryService service, String action) {
            super(action, "List assignments or retire a registered parcel recipient.");
            this.service = service;
            this.action = action;
            requirePermission("thankmasdelivery.admin");
            id = withRequiredArg("id", "Registered recipient ID", ArgTypes.STRING);
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player, World world) {
            String recipientId = context.get(id);
            Destination destination = service.deliveryStore().recipient(recipientId);
            if (destination == null) {
                context.sendMessage(Message.raw("No recipient registered with ID " + recipientId + "."));
                return;
            }
            if (action.equals("assignments")) {
                List<Profile> affected = service.deliveryStore().assignmentsFor(recipientId);
                context.sendMessage(Message.raw(affected.size() + " active deliveries for " + recipientId + ":"));
                for (Profile profile : affected) {
                    context.sendMessage(Message.raw(profile.name() + " (" + profile.owner() + ")"));
                }
                return;
            }
            World targetWorld = Universe.get().getWorld(destination.worldId());
            if (action.equals("retire")) {
                if (!service.reserveRecipient(recipientId)) {
                    context.sendMessage(Message.raw("A change to this recipient is already pending."));
                    return;
                }
                service.adminAction(recipientId, world, player, () -> service.deliveryStore().retire(recipientId), count -> {
                    if (targetWorld != null) {
                        targetWorld.execute(() -> {
                            Ref<EntityStore> npc = targetWorld.getEntityRef(destination.entityId());
                            if (npc != null && npc.isValid()) {
                                npc.getStore().removeEntity(npc, RemoveReason.REMOVE);
                            }
                        });
                    }
                    context.sendMessage(Message.raw("Retired " + recipientId + ". Abandoned " + count + " deliveries without points."));
                }, () -> { });
                return;
            }
        }
    }
}
