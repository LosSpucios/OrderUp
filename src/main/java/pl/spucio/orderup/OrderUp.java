package pl.spucio.orderup;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import pl.spucio.orderup.network.OrderUpNetworking;

@Mod(OrderUp.MOD_ID)
public final class OrderUp {
    public static final String MOD_ID = "orderup";

    public OrderUp(IEventBus modBus) {
        ModContent.register(modBus);
        ModParticles.register(modBus);
        modBus.addListener(ModContent::registerEntityAttributes);
        modBus.addListener(OrderUpNetworking::registerPayloads);
    }
}
