package acorn.thankmasdelivery.command;

import acorn.thankmasdelivery.effect.DeliveryEffectRules;
import acorn.thankmasdelivery.service.DeliveryService;
import acorn.thankmasdelivery.util.DeliveryMessages;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DeliveryCommand extends AbstractPlayerCommand {
    private final DeliveryService deliveryService;

    public DeliveryCommand(DeliveryService deliveryService) {
        super("parcel", "Thankmas Delivery commands.");
        this.deliveryService = deliveryService;
        requireNoPermission();
        addSubCommand(new ActionCommand("choices", "Show your three parcel choices.", deliveryService));
        addSubCommand(new ChooseCommand(deliveryService));
        addSubCommand(new TestCommand(deliveryService));
        addSubCommand(new ActionCommand("effectlist", "List available parcel effects and their descriptions.", deliveryService));
        addSubCommand(new ActionCommand("deliver", "Show instructions for handing your parcel to its recipient.", deliveryService));
        addSubCommand(new ActionCommand("stop", "Remove your parcel and all assignment effects without points.", deliveryService));
        addSubCommand(new DeliveryNpcCommand(deliveryService));
    }

    @Override
    protected void execute(
        CommandContext context,
        Store<EntityStore> store,
        Ref<EntityStore> entity,
        PlayerRef player,
        World world
    ) {
        deliveryService.choices(player);
    }

    private static final class ActionCommand extends AbstractPlayerCommand {
        private final String action;
        private final DeliveryService deliveryService;

        ActionCommand(String action, String description, DeliveryService deliveryService) {
            super(action, description);
            this.action = action;
            this.deliveryService = deliveryService;
            requireNoPermission();
            if (action.equals("stop")) {
                addAliases("remove", "leave");
            }
        }

        @Override
        protected void execute(
            CommandContext context,
            Store<EntityStore> store,
            Ref<EntityStore> entity,
            PlayerRef player,
            World world
        ) {
            if (action.equals("choices")) {
                deliveryService.choices(player);
                return;
            }
            if (action.equals("stop")) {
                deliveryService.stop(player);
                return;
            }
            reply(context, switch (action) {
                case "effectlist" -> DeliveryMessages.effectList();
                case "deliver" -> Message.raw(deliveryService.deliver(player));
                default -> throw new IllegalStateException("Unknown parcel action: " + action);
            });
        }
    }

    private static final class ChooseCommand extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final RequiredArg<Integer> option;

        ChooseCommand(DeliveryService service) {
            super("choose", "Accept parcel 1, 2, or 3 while beside the dispatcher.");
            this.service = service;
            requireNoPermission();
            option = withRequiredArg("option", "Parcel number: 1, 2, or 3", ArgTypes.INTEGER);
        }

        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> entity,
            PlayerRef player, World world) {
            service.choose(store, entity, player, context.get(option));
        }
    }

    private static final class TestCommand extends AbstractPlayerCommand {
        private final DeliveryService service;
        private final RequiredArg<String> effects;
        TestCommand(DeliveryService service) {
            super("test", "Start a test parcel with comma-separated effects, without points.");
            this.service = service;
            requirePermission("thankmasdelivery.admin");
            effects = withRequiredArg("effects", "Effect names separated by commas", ArgTypes.GREEDY_STRING);
        }
        @Override
        protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> entity,
            PlayerRef player, World world) {
            try { service.test(store, entity, player, DeliveryEffectRules.parseSelection(context.get(effects))); }
            catch (IllegalArgumentException invalid) {
                reply(context, invalid.getMessage());
            }
        }
    }

    private static void reply(CommandContext context, String text) {
        reply(context, Message.raw(text));
    }

    private static void reply(CommandContext context, Message text) {
        context.sendMessage(DeliveryMessages.withPrefix(text));
    }
}
