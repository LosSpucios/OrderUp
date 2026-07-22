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

    public CustomerRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CustomerEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(CustomerEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
        if (!ClientRestaurantState.canSeeCustomerThoughts(entity.getRestaurantHeart())) return;
        if (entity.getCustomerState() != CustomerEntity.THINKING && entity.getCustomerState() != CustomerEntity.WAITING) return;

        poseStack.pushPose();
        poseStack.translate(0.0D, entity.getBbHeight() + 0.75D, 0.0D);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        if (entity.getCustomerState() == CustomerEntity.THINKING) {
            int dots = 1 + (entity.tickCount / 10) % 3;
            String text = ".".repeat(dots);
            drawBubbleText(poseStack, buffer, text, packedLight);
        } else {
            drawOrderItems(entity, poseStack, buffer, packedLight);
        }
        poseStack.popPose();
    }

    private void drawBubbleText(PoseStack poseStack, MultiBufferSource buffer, String text, int packedLight) {
        Font font = Minecraft.getInstance().font;
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(
                Component.literal(text),
                x,
                0,
                0xFF252525,
                false,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0xEFFFFFFF,
                packedLight
        );
    }

    private void drawOrderItems(CustomerEntity entity, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        ItemStack food = entity.getOrderedFood();
        ItemStack drink = entity.getOrderedDrink();
        int count = drink.isEmpty() ? 1 : 2;
        float startX = count == 1 ? 0.0F : -10.0F;

        poseStack.pushPose();
        poseStack.translate(startX, 0.0F, 0.0F);
        poseStack.scale(16.0F, 16.0F, 16.0F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                food,
                ItemDisplayContext.GUI,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );
        poseStack.popPose();

        if (!drink.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(10.0F, 0.0F, 0.0F);
            poseStack.scale(16.0F, 16.0F, 16.0F);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    drink,
                    ItemDisplayContext.GUI,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    buffer,
                    entity.level(),
                    entity.getId() + 1
            );
            poseStack.popPose();
        }
    }
}
