package net.spucio.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.network.OrderUpNetworking;

@EventBusSubscriber(modid = OrderUp.MOD_ID, value = Dist.CLIENT)
public final class ClientGameEvents {
    private static final int HUD_PANEL_BACKGROUND = 0xAA2B2018;
    private static final int HUD_TEXT = 0xFFF3E2BF;
    private static final int HUD_RED = 0xFFFF5C52;
    private static final int HUD_GREEN = 0xFF69D06F;
    private static final double BORDER_EPSILON = 0.002D;

    private ClientGameEvents() {}

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        if (!ClientRestaurantState.hudActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        renderOpenClosedSign(graphics, minecraft, width);
        renderMoney(graphics, minecraft, width, height);
        renderXp(graphics, minecraft, width, height);
        renderRestaurantStatus(graphics, minecraft, height);
    }

    private static void renderOpenClosedSign(GuiGraphics graphics, Minecraft minecraft, int width) {
        boolean open = ClientRestaurantState.openSignPresent() && ClientRestaurantState.restaurantOpen();
        String text = open ? "OPEN" : "CLOSED";

        int panelWidth = open ? 112 : 128;
        int panelHeight = 30;
        int x = (width - panelWidth) / 2;
        int y = 8;

        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xFF4C2E1E);
        graphics.fill(x + 2, y + 2, x + panelWidth - 2, y + panelHeight - 2, 0xFFC89B55);
        graphics.fill(
                x + 5,
                y + 5,
                x + panelWidth - 5,
                y + panelHeight - 5,
                open ? 0xFF477A4B : 0xFFA4473C
        );
        graphics.fill(x + 7, y + 7, x + panelWidth - 7, y + 9, open ? 0xFF6A9A68 : 0xFFC66A5B);

        int textX = x + (panelWidth - minecraft.font.width(text)) / 2;
        graphics.drawString(
                minecraft.font,
                text,
                textX,
                y + 11,
                open ? 0xFFE4F0D6 : 0xFFFFE2D6,
                true
        );
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
    public static void handleBoundaryPurchaseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) return;
        if (!minecraft.player.isShiftKeyDown() || !ClientRestaurantState.borderActive()
                || !ClientRestaurantState.borderOwner()) return;

        HitResult vanillaHit = minecraft.hitResult;
        if (vanillaHit instanceof BlockHitResult blockHit
                && minecraft.level.getBlockState(blockHit.getBlockPos()).is(ModContent.RESTAURANT_HEART.get())) {
            // Let Shift + RMB on the Heart keep toggling the preview.
            return;
        }

        Vec3 eye = minecraft.player.getEyePosition(1.0F);
        double maxDistance = 64.0D;
        if (vanillaHit != null && vanillaHit.getType() != HitResult.Type.MISS) {
            maxDistance = Math.min(maxDistance, eye.distanceTo(vanillaHit.getLocation()) + 0.15D);
        }

        ClientRestaurantState.ExpansionTarget target = ClientRestaurantState.findLookedAtExpansion(
                eye,
                minecraft.player.getViewVector(1.0F),
                maxDistance,
                minecraft.level.getMinBuildHeight(),
                minecraft.level.getMaxBuildHeight()
        );
        if (target == null) return;

        PacketDistributor.sendToServer(new OrderUpNetworking.ExpandRestaurantPayload(
                ClientRestaurantState.borderHeart(),
                target.chunkX(),
                target.chunkZ()
        ));
        event.setCanceled(true);
        event.setSwingHand(true);
    }

    @SubscribeEvent
    public static void renderRestaurantBorder(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!ClientRestaurantState.borderActive()) return;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        Vec3 camera = event.getCamera().getPosition();
        int minY = minecraft.level.getMinBuildHeight();
        int maxY = minecraft.level.getMaxBuildHeight();
        PoseStack poseStack = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());

        for (ClientRestaurantState.BoundaryEdge edge : ClientRestaurantState.borderEdges()) {
            AABB faceBox = boundaryFaceBox(edge, minY, maxY).move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(poseStack, lines, faceBox, 1.0F, 1.0F, 1.0F, 0.92F);
        }
        buffers.endBatch(RenderType.lines());

        if (ClientRestaurantState.borderOwner()) {
            /*
             * Expansion labels are anchored to the Restaurant Heart rather than
             * the viewer. This keeps every label at a stable world position while
             * the player walks, jumps or flies around the claimed chunks.
             */
            double labelY = Math.max(
                    minY + 3.0D,
                    Math.min(maxY - 3.0D, ClientRestaurantState.borderHeart().getY() + 6.0D)
            );
            for (ClientRestaurantState.BoundaryEdge edge : ClientRestaurantState.borderEdges()) {
                renderExpansionLabel(edge, labelY, camera, poseStack, buffers, minecraft);
            }
            buffers.endBatch();
        }
    }

    private static AABB boundaryFaceBox(
            ClientRestaurantState.BoundaryEdge edge,
            int minY,
            int maxY
    ) {
        ChunkPos chunk = new ChunkPos(edge.chunkX(), edge.chunkZ());
        return switch (edge.direction()) {
            case EAST -> new AABB(
                    chunk.getMaxBlockX() + 1.0D - BORDER_EPSILON,
                    minY,
                    chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1.0D + BORDER_EPSILON,
                    maxY,
                    chunk.getMaxBlockZ() + 1.0D
            );
            case WEST -> new AABB(
                    chunk.getMinBlockX() - BORDER_EPSILON,
                    minY,
                    chunk.getMinBlockZ(),
                    chunk.getMinBlockX() + BORDER_EPSILON,
                    maxY,
                    chunk.getMaxBlockZ() + 1.0D
            );
            case SOUTH -> new AABB(
                    chunk.getMinBlockX(),
                    minY,
                    chunk.getMaxBlockZ() + 1.0D - BORDER_EPSILON,
                    chunk.getMaxBlockX() + 1.0D,
                    maxY,
                    chunk.getMaxBlockZ() + 1.0D + BORDER_EPSILON
            );
            case NORTH -> new AABB(
                    chunk.getMinBlockX(),
                    minY,
                    chunk.getMinBlockZ() - BORDER_EPSILON,
                    chunk.getMaxBlockX() + 1.0D,
                    maxY,
                    chunk.getMinBlockZ() + BORDER_EPSILON
            );
            default -> throw new IllegalArgumentException("Vertical restaurant boundary");
        };
    }

    private static void renderExpansionLabel(
            ClientRestaurantState.BoundaryEdge edge,
            double y,
            Vec3 camera,
            PoseStack poseStack,
            MultiBufferSource.BufferSource buffers,
            Minecraft minecraft
    ) {
        ChunkPos chunk = new ChunkPos(edge.chunkX(), edge.chunkZ());
        double x;
        double z;
        float fixedYaw;

        /*
         * Each label is permanently aligned with its boundary wall and faces the
         * restaurant interior. It no longer billboards toward the camera.
         */
        switch (edge.direction()) {
            case EAST -> {
                x = chunk.getMaxBlockX() + 1.02D;
                z = chunk.getMinBlockZ() + 8.0D;
                fixedYaw = 90.0F;
            }
            case WEST -> {
                x = chunk.getMinBlockX() - 0.02D;
                z = chunk.getMinBlockZ() + 8.0D;
                fixedYaw = -90.0F;
            }
            case SOUTH -> {
                x = chunk.getMinBlockX() + 8.0D;
                z = chunk.getMaxBlockZ() + 1.02D;
                fixedYaw = 0.0F;
            }
            case NORTH -> {
                x = chunk.getMinBlockX() + 8.0D;
                z = chunk.getMinBlockZ() - 0.02D;
                fixedYaw = 180.0F;
            }
            default -> {
                return;
            }
        }

        String text = ClientRestaurantState.expansionLabel();
        Font font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(x - camera.x, y - camera.y, z - camera.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(fixedYaw));

        // Roughly 3.5–5 blocks wide for the usual price/requirement labels.
        poseStack.scale(-0.15F, -0.15F, 0.15F);
        font.drawInBatch(
                text,
                -font.width(text) / 2.0F,
                -font.lineHeight / 2.0F,
                ClientRestaurantState.expansionLabelColor(),
                true,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.SEE_THROUGH,
                0,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }
}
