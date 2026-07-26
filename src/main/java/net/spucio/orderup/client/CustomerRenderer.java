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
        poseStack.translate(0.0F, -12.0F, -0.22F);
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

        drawBubbleTexture(
                poseStack,
                buffer,
                hasDrink ? 104.0F : 80.0F,
                hasDrink ? 56.0F : 52.0F,
                packedLight
        );

        float foodX = hasDrink ? -18.0F : 0.0F;
        renderOrderItem(entity, food, foodX, -17.0F, poseStack, buffer, packedLight, entity.getId());
        renderStatusMark(
                entity,
                foodX + 8.0F,
                -29.0F,
                entity.isFoodDelivered(),
                poseStack,
                buffer,
                packedLight
        );

        if (hasDrink) {
            float drinkX = 18.0F;
            renderOrderItem(entity, drink, drinkX, -17.0F, poseStack, buffer, packedLight, entity.getId() + 1);
            renderStatusMark(
                    entity,
                    drinkX + 8.0F,
                    -29.0F,
                    entity.isDrinkDelivered(),
                    poseStack,
                    buffer,
                    packedLight
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

    private void renderStatusMark(
            CustomerEntity entity,
            float x,
            float y,
            boolean delivered,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        String mark;
        int background;
        if (entity.isOrderFailed()) {
            mark = "X";
            background = 0xE0B8322D;
        } else if (delivered) {
            mark = "✓";
            background = 0xE0328A43;
        } else {
            return;
        }

        Font font = Minecraft.getInstance().font;
        poseStack.pushPose();
        poseStack.translate(x, y, -0.26F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        font.drawInBatch(
                Component.literal(mark),
                -font.width(mark) / 2.0F,
                0.0F,
                0xFFFFFFFF,
                true,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                background,
                packedLight
        );
        poseStack.popPose();
    }
}
