package net.spucio.orderup.event;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.price.IngredientPriceManager;
import net.spucio.orderup.restaurant.RestaurantManager;
import net.spucio.orderup.util.MoneyFormatter;

@EventBusSubscriber(modid = OrderUp.MOD_ID)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        IngredientPriceManager.load();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) RestaurantManager.clear(level);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("orderup")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("money")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> changeRestaurantMoney(context, true))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> changeRestaurantMoney(context, false)))))
        );
    }

    private static int changeRestaurantMoney(
            CommandContext<CommandSourceStack> context,
            boolean set
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        RestaurantHeartBlockEntity heart = RestaurantManager
                .findContaining(player.serverLevel(), player.blockPosition())
                .orElse(null);

        if (heart == null) {
            source.sendFailure(Component.literal("You are not standing inside a restaurant."));
            return 0;
        }

        double dollars = DoubleArgumentType.getDouble(context, "amount");
        double doubled = dollars * 2.0D;
        if (!Double.isFinite(doubled) || Math.abs(doubled - Math.rint(doubled)) > 0.000001D) {
            source.sendFailure(Component.literal("Money must use increments of $0.5."));
            return 0;
        }

        long halfUnits = MoneyFormatter.dollarsToHalfUnits(dollars);
        if (set) {
            heart.setMoneyHalfUnits(halfUnits);
        } else {
            heart.addMoneyHalfUnits(halfUnits);
        }

        source.sendSuccess(
                () -> Component.literal(
                        "Restaurant money " + (set ? "set to " : "increased to ")
                                + MoneyFormatter.withDollarPrefix(heart.getMoney()) + "."
                ),
                true
        );
        return 1;
    }
}
