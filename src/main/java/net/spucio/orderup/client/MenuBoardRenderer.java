package net.spucio.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;

public class MenuBoardRenderer implements BlockEntityRenderer<MenuBoardBlockEntity> {
    public MenuBoardRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MenuBoardBlockEntity menu, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        for (int i = 0; i < MenuBoardBlockEntity.SLOT_COUNT; i++) {
            ItemStack stack = menu.getGhostItem(i);
            if (stack.isEmpty()) continue;
            int col = i % 3;
            int row = i / 3;
            poseStack.pushPose();
            poseStack.translate(0.27D + col * 0.23D, 1.02D, 0.36D + row * 0.28D);
            poseStack.scale(0.23F, 0.23F, 0.23F);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    menu.getLevel(),
                    (int) menu.getBlockPos().asLong() + i
            );
            poseStack.popPose();
        }
    }
}
