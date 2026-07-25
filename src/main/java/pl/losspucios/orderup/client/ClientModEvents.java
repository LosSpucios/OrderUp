package pl.losspucios.orderup.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import pl.losspucios.orderup.ModContent;
import pl.losspucios.orderup.ModParticles;
import pl.losspucios.orderup.OrderUp;
import pl.losspucios.orderup.client.particle.CoinParticle;

@EventBusSubscriber(modid = OrderUp.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.COIN.get(), CoinParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.CUSTOMER.get(), CustomerRenderer::new);
        event.registerBlockEntityRenderer(ModContent.MENU_BOARD_BE.get(), MenuBoardRenderer::new);
        event.registerBlockEntityRenderer(ModContent.OPEN_SIGN_BE.get(), OpenSignRenderer::new);
    }
}
