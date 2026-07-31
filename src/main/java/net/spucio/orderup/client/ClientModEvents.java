package net.spucio.orderup.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.ModParticles;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.client.particle.CoinParticle;
import net.spucio.orderup.client.particle.RestaurantXpParticle;

@EventBusSubscriber(modid = OrderUp.MOD_ID, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.COIN.get(), CoinParticle.Provider::new);
        event.registerSpriteSet(ModParticles.RESTAURANT_XP.get(), RestaurantXpParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModContent.CUSTOMER.get(), CustomerRenderer::new);
        event.registerBlockEntityRenderer(ModContent.MENU_BOARD_BE.get(), MenuBoardRenderer::new);
        event.registerBlockEntityRenderer(ModContent.OPEN_SIGN_BE.get(), OpenSignRenderer::new);
    }
}
