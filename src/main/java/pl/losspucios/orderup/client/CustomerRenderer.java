package pl.losspucios.orderup.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import pl.losspucios.orderup.entity.CustomerEntity;

public class CustomerRenderer extends MobRenderer<CustomerEntity, VillagerModel<CustomerEntity>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");
    private static final int BUBBLE_BORDER = 0xEE6E5239;
    private static final int BUBBLE_BACKGROUND = 0xF6FFF2D5;

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
        poseStack.translate(0.0D, entity.getBbHeight() + 0.75D, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        if (state == CustomerEntity.THINKING) {
            int dots = 1 + (entity.tickCount / 10) % 3;
            drawThinkingBubble(poseStack, buffer, ".".repeat(dots), packedLight);
        } else {
            drawOrderBubble(entity, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
    }

    private void drawThinkingBubble(PoseStack poseStack, MultiBufferSource buffer, String text, int packedLight) {
        Font font = Minecraft.getInstance().font;
        drawBubbleBackground(poseStack, buffer, 38, packedLight);
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(
                Component.literal(text),
                x,
                0,
                0xFF2B211A,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                packedLight
        );
    }

    private void drawOrderBubble(CustomerEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack food = entity.getOrderedFood();
        ItemStack drink = entity.getOrderedDrink();
        boolean hasDrink = !drink.isEmpty();
        int bubbleWidth = hasDrink ? 58 : 38;
        drawBubbleBackground(poseStack, buffer, bubbleWidth, packedLight);

        float foodX = hasDrink ? -10.0F : 0.0F;
        renderOrderItem(entity, food, foodX, poseStack, buffer, packedLight, entity.getId());
        renderStatusMark(
                entity,
                foodX + 5.0F,
                entity.isFoodDelivered(),
                poseStack,
                buffer,
                packedLight
        );

        if (hasDrink) {
            float drinkX = 10.0F;
            renderOrderItem(entity, drink, drinkX, poseStack, buffer, packedLight, entity.getId() + 1);
            renderStatusMark(
                    entity,
                    drinkX + 5.0F,
                    entity.isDrinkDelivered(),
                    poseStack,
                    buffer,
                    packedLight
            );
        }
    }

    private void drawBubbleBackground(PoseStack poseStack, MultiBufferSource buffer, int width, int packedLight) {
        Font font = Minecraft.getInstance().font;
        String outer = " ".repeat(Math.max(1, (width + 3) / 4));
        String inner = " ".repeat(Math.max(1, width / 4));

        font.drawInBatch(
                Component.literal(outer),
                -font.width(outer) / 2.0F,
                -4.0F,
                0x00FFFFFF,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                BUBBLE_BORDER,
                packedLight
        );
        font.drawInBatch(
                Component.literal(inner),
                -font.width(inner) / 2.0F,
                -3.0F,
                0x00FFFFFF,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                BUBBLE_BACKGROUND,
                packedLight
        );
    }

    private void renderOrderItem(
            CustomerEntity entity,
            ItemStack stack,
            float x,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int seed
    ) {
        poseStack.pushPose();
        poseStack.translate(x, 0.0F, 0.0F);
        poseStack.scale(16.0F, 16.0F, 16.0F);
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
            boolean delivered,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight
    ) {
        String mark;
        int color;
        if (entity.isOrderFailed()) {
            mark = "✕";
            color = 0xFFFF4B3E;
        } else if (delivered) {
            mark = "✓";
            color = 0xFF55D96B;
        } else {
            return;
        }

        Minecraft.getInstance().font.drawInBatch(
                Component.literal(mark),
                x,
                7.0F,
                color,
                true,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0,
                packedLight
        );
    }
}
