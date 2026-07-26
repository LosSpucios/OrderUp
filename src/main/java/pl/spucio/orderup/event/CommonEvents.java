package pl.spucio.orderup.event;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import pl.spucio.orderup.OrderUp;
import pl.spucio.orderup.price.IngredientPriceManager;
import pl.spucio.orderup.restaurant.RestaurantManager;

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
}
