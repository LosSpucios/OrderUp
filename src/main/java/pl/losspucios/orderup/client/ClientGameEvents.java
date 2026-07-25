package pl.losspucios.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import pl.losspucios.orderup.OrderUp;

@EventBusSubscriber(modid = OrderUp.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientGameEvents {
    private ClientGameEvents() {}

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!ClientRestaurantState.hudActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;

        var graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        graphics.fill(width / 2 - 132, height - 48, width / 2 - 64, height - 28, 0xAA2B2018);
        graphics.drawString(minecraft.font, "$" + ClientRestaurantState.money(), width / 2 - 124, height - 42, 0xFF7EE081, true);

        int x = width - 18;
        int y = height / 2 - 60;
        int h = 120;
        graphics.fill(x, y, x + 8, y + h, 0xAA2A241E);

        double progress = Math.min(1.0D, ClientRestaurantState.xp() / (double) Math.max(1, ClientRestaurantState.nextXp()));
        int filled = (int) Math.round(h * progress);
        if (filled > 0) {
            graphics.fill(x + 1, y + h - filled, x + 7, y + h - 1, 0xFF73B85D);
        }

        graphics.drawString(minecraft.font, "Lv." + ClientRestaurantState.level(), x - 24, y - 10, 0xFFFFFFFF, true);
    }

    @SubscribeEvent
    public static void renderRestaurantBorder(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        BlockPos heart = ClientRestaurantState.borderHeart();
        if (heart == null) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int radius = ClientRestaurantState.borderRadius();
        int verticalHeight = 8;
        Vec3 camera = event.getCamera().getPosition();

        AABB box = new AABB(
                heart.getX() - radius,
                heart.getY() - 0.02D,
                heart.getZ() - radius,
                heart.getX() + radius + 1.0D,
                heart.getY() + verticalHeight + 1.0D,
                heart.getZ() + radius + 1.0D
        ).move(-camera.x, -camera.y, -camera.z);

        PoseStack poseStack = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(poseStack, lines, box, 1.0F, 1.0F, 1.0F, 0.9F);
        buffers.endBatch(RenderType.lines());
    }
}
