package pl.losspucios.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import pl.losspucios.orderup.ModContent;
import pl.losspucios.orderup.OrderUp;

@EventBusSubscriber(modid = OrderUp.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static final int HUD_PANEL_BACKGROUND = 0xAA2B2018;
    private static final int HUD_TEXT = 0xFFF3E2BF;
    private static final int HUD_RED = 0xFFFF5C52;
    private static final int HUD_GREEN = 0xFF69D06F;

    private ClientGameEvents() {}

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!ClientRestaurantState.hudActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        renderMoney(graphics, minecraft, width, height);
        renderXp(graphics, minecraft, width, height);
        renderRestaurantStatus(graphics, minecraft, height);
    }

    private static void renderMoney(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        graphics.fill(width / 2 - 132, height - 48, width / 2 - 64, height - 28, HUD_PANEL_BACKGROUND);
        graphics.drawString(
                minecraft.font,
                "$" + ClientRestaurantState.money(),
                width / 2 - 124,
                height - 42,
                0xFF7EE081,
                true
        );
    }

    private static void renderXp(GuiGraphics graphics, Minecraft minecraft, int width, int height) {
        int x = width - 18;
        int y = height / 2 - 60;
        int barHeight = 120;
        graphics.fill(x, y, x + 8, y + barHeight, HUD_PANEL_BACKGROUND);

        double progress = Math.min(
                1.0D,
                ClientRestaurantState.xp() / (double) Math.max(1, ClientRestaurantState.nextXp())
        );
        int filled = (int) Math.round(barHeight * progress);
        if (filled > 0) {
            graphics.fill(x + 1, y + barHeight - filled, x + 7, y + barHeight - 1, 0xFF73B85D);
        }

        graphics.drawString(
                minecraft.font,
                "Lv." + ClientRestaurantState.level(),
                x - 24,
                y - 10,
                0xFFFFFFFF,
                true
        );
    }

    private static void renderRestaurantStatus(GuiGraphics graphics, Minecraft minecraft, int height) {
        int panelX = 12;
        int panelY = height / 2 - 25;
        int panelWidth = 76;
        int panelHeight = 50;

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, HUD_PANEL_BACKGROUND);
        graphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, 0x552A211B);

        ItemStack chairIcon = ModContent.CHAIR_ITEM.get().getDefaultInstance();
        graphics.renderItem(chairIcon, panelX + 5, panelY + 5);

        String chairText = ClientRestaurantState.occupiedChairs() + "/" + ClientRestaurantState.totalChairs();
        int chairColor = ClientRestaurantState.totalChairs() == 0 ? HUD_RED : HUD_TEXT;
        graphics.drawString(minecraft.font, chairText, panelX + 27, panelY + 9, chairColor, true);

        ItemStack menuIcon = ModContent.MENU_BOARD_ITEM.get().getDefaultInstance();
        graphics.renderItem(menuIcon, panelX + 5, panelY + 28);

        String menuState = ClientRestaurantState.menuComplete() ? "✓" : "✕";
        int menuColor = ClientRestaurantState.menuComplete() ? HUD_GREEN : HUD_RED;
        graphics.drawString(minecraft.font, menuState, panelX + 29, panelY + 32, menuColor, true);
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
