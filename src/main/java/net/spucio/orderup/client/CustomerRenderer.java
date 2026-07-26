package net.spucio.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.entity.CustomerEntity;

public class CustomerRenderer extends MobRenderer<CustomerEntity, VillagerModel<CustomerEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final ResourceLocation THOUGHT_BUBBLE =
            ResourceLocation.fromNamespaceAndPath(OrderUp.MOD_ID, "textures/entity/thought_bubble.png");

    private static final float BILLBOARD_SCALE = 0.025F;
    private static final float ITEM_SCALE = 14.0F;

    public CustomerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CustomerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            CustomerEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        if (!ClientRestaurantState.canSeeCustomerThoughts(entity.getRestaurantHeart())) return;

        int state = entity.getCustomerState();
        if (state != CustomerEntity.THINKING
                && state != CustomerEntity.WAITING
                && state != CustomerEntity.REACTING) {
            return;
        }

        poseStack.pushPose();

        /*
         * Keep the entire cloud above the villager model. This prevents the head
         * from depth-clipping the lower part of the texture.
         */
        poseStack.translate(0.0D, entity.getBbHeight() + 0.82D, 0.0D);

        // Yaw-only billboard: follows the viewer around the customer on the X/Z plane.
        poseStack.mulPose(Axis.YP.rotationDegrees(-entityRenderDispatcher.camera.getYRot()));
        poseStack.scale(-BILLBOARD_SCALE, -BILLBOARD_SCALE, BILLBOARD_SCALE);

        if (state == CustomerEntity.THINKING) {
            drawBubbleTexture(poseStack, buffer, 80.0F, 44.0F, packedLight);
            drawThinkingDots(entity, poseStack, buffer, packedLight);
        } else {
            drawOrderBubble(entity, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
    }

    private void drawThinkingDots(
            CustomerEntity entity,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        String text = ".".repeat(1 + (entity.tickCount / 10) % 3);
        Font font = Minecraft.getInstance().font;

        poseStack.pushPose();
        poseStack.translate(0.0F, -31.0F, -0.22F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        font.drawInBatch(
                Component.literal(text),
                -font.width(text) / 2.0F,
                0.0F,
                0xFF3A2A1E,
                true,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                packedLight
        );
        poseStack.popPose();
    }

    private void drawOrderBubble(
            CustomerEntity entity,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        ItemStack food = entity.getOrderedFood();
        ItemStack drink = entity.getOrderedDrink();
        boolean hasDrink = !drink.isEmpty();
        float bubbleWidth = hasDrink ? 104.0F : 80.0F;
        float bubbleHeight = hasDrink ? 56.0F : 52.0F;

        drawBubbleTexture(poseStack, buffer, bubbleWidth, bubbleHeight, packedLight);

        float foodX = hasDrink ? -18.0F : 0.0F;
        float drinkX = 18.0F;
        float itemY = -17.0F;

        // Render every requested item first. The item renderer may use several buffered
        // render types, so result marks are deliberately drawn only after both items.
        renderOrderItem(entity, food, foodX, itemY, poseStack, buffer, packedLight, entity.getId());
        if (hasDrink) {
            renderOrderItem(entity, drink, drinkX, itemY, poseStack, buffer, packedLight, entity.getId() + 1);
        }

        /*
         * Flush the item layers before drawing status marks. Without this, Minecraft can
         * submit a buffered item layer after the check/X even though the Java call happened
         * earlier, which makes the item cover the mark.
         */
        if (buffer instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        renderItemResultOverlay(
                entity,
                foodX,
                itemY,
                entity.isFoodDelivered(),
                poseStack,
                buffer,
                packedLight
        );

        if (hasDrink) {
            renderItemResultOverlay(
                    entity,
                    drinkX,
                    itemY,
                    entity.isDrinkDelivered(),
                    poseStack,
                    buffer,
                    packedLight
            );
        }

        boolean complete = entity.isFoodDelivered() && (!hasDrink || entity.isDrinkDelivered());
        if (entity.isOrderFailed() || complete) {
            renderCornerResultBadge(
                    entity.isOrderFailed(),
                    bubbleWidth / 2.0F - 13.0F,
                    -3.0F,
                    poseStack,
                    buffer
            );
        }
    }

    private void drawBubbleTexture(
            PoseStack poseStack,
            MultiBufferSource buffer,
            float width,
            float height,
            int packedLight
    ) {
        float minX = -width / 2.0F;
        float maxX = width / 2.0F;
        float minY = -height + 8.0F;
        float maxY = 8.0F;

        /*
         * The asset uses hard alpha edges, so cutout-no-cull keeps the pixel-art
         * border crisp and visible from either side.
         */
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(THOUGHT_BUBBLE));
        PoseStack.Pose pose = poseStack.last();
        bubbleVertex(consumer, pose, minX, maxY, 0.08F, 0.0F, 1.0F, packedLight);
        bubbleVertex(consumer, pose, maxX, maxY, 0.08F, 1.0F, 1.0F, packedLight);
        bubbleVertex(consumer, pose, maxX, minY, 0.08F, 1.0F, 0.0F, packedLight);
        bubbleVertex(consumer, pose, minX, minY, 0.08F, 0.0F, 0.0F, packedLight);
    }

    private static void bubbleVertex(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float x,
            float y,
            float z,
            float u,
            float v,
            int packedLight
    ) {
        consumer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    private void renderOrderItem(
            CustomerEntity entity,
            ItemStack stack,
            float x,
            float y,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int seed
    ) {
        if (stack.isEmpty()) return;

        poseStack.pushPose();
        poseStack.translate(x, y, -0.18F);

        /*
         * Do not undo the billboard mirror with a negative X scale. A single
         * negative axis changes the matrix handedness and makes many item
         * models render edge-on or get culled. A 180-degree Z rotation cancels both billboard axis flips, so the
         * item remains upright and non-mirrored without changing matrix
         * handedness for the item renderer.
         */
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GUI,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                seed
        );
        poseStack.popPose();
    }

    private void renderItemResultOverlay(
            CustomerEntity entity,
            float x,
            float y,
            boolean delivered,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        if (!entity.isOrderFailed() && !delivered) return;

        int color = entity.isOrderFailed() ? 0xFFFF3B30 : 0xFF39D353;

        poseStack.pushPose();
        // A large negative Z offset places the mark safely in front of the GUI item model.
        poseStack.translate(x + 1.0F, y + 2.0F, -10.0F);
        // The root billboard has both X and Y inverted. This rotation cancels that inversion
        // only for the status artwork, keeping the check and X in their normal orientation.
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        if (entity.isOrderFailed()) {
            drawPixelCross(poseStack, buffer, 13.0F, 2.7F, 0.0F, color);
        } else {
            drawPixelCheck(poseStack, buffer, 13.0F, 2.7F, 0.0F, color);
        }
        poseStack.popPose();
    }

    private void renderCornerResultBadge(
            boolean failed,
            float x,
            float y,
            PoseStack poseStack,
            MultiBufferSource buffer
    ) {
        int fill = failed ? 0xFFF04A3E : 0xFF2FA84F;
        int border = failed ? 0xFF7A1F1A : 0xFF145C28;

        poseStack.pushPose();
        poseStack.translate(x, y, -10.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));

        drawFlatRect(poseStack, buffer, -10.0F, -10.0F, 10.0F, 10.0F, 0.0F, border);
        drawFlatRect(poseStack, buffer, -8.0F, -8.0F, 8.0F, 8.0F, -0.02F, fill);

        if (failed) {
            drawPixelCross(poseStack, buffer, 11.0F, 2.5F, -0.05F, 0xFFFFFFFF);
        } else {
            drawPixelCheck(poseStack, buffer, 11.0F, 2.5F, -0.05F, 0xFFFFFFFF);
        }
        poseStack.popPose();
    }

    private static void drawPixelCheck(
            PoseStack poseStack,
            MultiBufferSource buffer,
            float size,
            float thickness,
            float z,
            int argb
    ) {
        float half = size / 2.0F;
        drawThickLine(
                poseStack,
                buffer,
                -half,
                0.0F,
                -size * 0.12F,
                -size * 0.34F,
                thickness,
                z,
                argb
        );
        drawThickLine(
                poseStack,
                buffer,
                -size * 0.12F,
                -size * 0.34F,
                half,
                size * 0.40F,
                thickness,
                z,
                argb
        );
    }

    private static void drawPixelCross(
            PoseStack poseStack,
            MultiBufferSource buffer,
            float size,
            float thickness,
            float z,
            int argb
    ) {
        float half = size / 2.0F;
        drawThickLine(poseStack, buffer, -half, -half, half, half, thickness, z, argb);
        drawThickLine(poseStack, buffer, -half, half, half, -half, thickness, z - 0.01F, argb);
    }

    private static void drawThickLine(
            PoseStack poseStack,
            MultiBufferSource buffer,
            float x1,
            float y1,
            float x2,
            float y2,
            float thickness,
            float z,
            int argb
    ) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.0001F) return;

        float normalX = -dy / length * thickness / 2.0F;
        float normalY = dx / length * thickness / 2.0F;
        int alpha = argb >>> 24 & 255;
        int red = argb >>> 16 & 255;
        int green = argb >>> 8 & 255;
        int blue = argb & 255;

        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, x1 + normalX, y1 + normalY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x2 + normalX, y2 + normalY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x2 - normalX, y2 - normalY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, x1 - normalX, y1 - normalY, z).setColor(red, green, blue, alpha);
    }

    private static void drawFlatRect(
            PoseStack poseStack,
            MultiBufferSource buffer,
            float minX,
            float minY,
            float maxX,
            float maxY,
            float z,
            int argb
    ) {
        int alpha = argb >>> 24 & 255;
        int red = argb >>> 16 & 255;
        int green = argb >>> 8 & 255;
        int blue = argb & 255;
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugQuads());
        PoseStack.Pose pose = poseStack.last();
        consumer.addVertex(pose, minX, maxY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, maxY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, maxX, minY, z).setColor(red, green, blue, alpha);
        consumer.addVertex(pose, minX, minY, z).setColor(red, green, blue, alpha);
    }

}
