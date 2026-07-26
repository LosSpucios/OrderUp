package net.spucio.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.spucio.orderup.block.OpenSignBlock;
import net.spucio.orderup.blockentity.OpenSignBlockEntity;

public class OpenSignRenderer implements BlockEntityRenderer<OpenSignBlockEntity> {
    private final Font font;

    public OpenSignRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(OpenSignBlockEntity sign, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        BlockState state = sign.getBlockState();
        boolean open = state.getValue(OpenSignBlock.OPEN);
        String text = open ? "OPEN" : "CLOSED";

        poseStack.pushPose();
        boolean wall = state.getValue(OpenSignBlock.WALL);
        poseStack.translate(0.5D, wall ? 0.59D : 0.72D, 0.5D);
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180.0F - state.getValue(OpenSignBlock.FACING).toYRot()));
        poseStack.translate(0.0D, 0.0D, wall ? 0.315D : 0.001D);
        poseStack.scale(-0.0125F, -0.0125F, 0.0125F);
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(
                Component.literal(text), x, 0.0F,
                open ? 0xFF173F24 : 0xFF5A1717,
                false, poseStack.last().pose(), buffer,
                Font.DisplayMode.POLYGON_OFFSET, 0, packedLight
        );
        poseStack.popPose();
    }
}
