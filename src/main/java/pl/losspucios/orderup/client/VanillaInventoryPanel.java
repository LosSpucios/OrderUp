package pl.losspucios.orderup.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Renders the exact player-inventory section from Minecraft's vanilla
 * generic container texture, then draws the player's real stacks on top.
 * The Order Up menu still treats those stacks as drag sources for ghost slots,
 * so nothing is consumed or moved by this helper.
 */
final class VanillaInventoryPanel {
    private static final ResourceLocation VANILLA_CONTAINER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/container/generic_54.png");

    static final int WIDTH = 176;
    static final int HEIGHT = 96;

    private static final int MAIN_START_X = 8;
    private static final int MAIN_START_Y = 13;
    private static final int HOTBAR_Y = 71;
    private static final int SLOT_STEP = 18;

    private VanillaInventoryPanel() {}

    static void render(
            GuiGraphics graphics,
            Font font,
            Inventory inventory,
            int startX,
            int startY,
            int mouseX,
            int mouseY,
            boolean showTooltip
    ) {
        // Bottom 96 px of the vanilla six-row container are the standard
        // 3x9 player inventory plus hotbar background.
        graphics.blit(VANILLA_CONTAINER_TEXTURE, startX, startY, 0, 126, WIDTH, HEIGHT);

        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventorySlot = 9 + row * 9 + col;
                int x = startX + MAIN_START_X + col * SLOT_STEP;
                int y = startY + MAIN_START_Y + row * SLOT_STEP;
                ItemStack stack = inventory.getItem(inventorySlot);
                renderStack(graphics, font, stack, x, y);

                if (showTooltip && isInside(mouseX, mouseY, x, y, 16, 16) && !stack.isEmpty()) {
                    hoveredStack = stack;
                }
            }
        }

        for (int col = 0; col < 9; col++) {
            int x = startX + MAIN_START_X + col * SLOT_STEP;
            int y = startY + HOTBAR_Y;
            ItemStack stack = inventory.getItem(col);
            renderStack(graphics, font, stack, x, y);

            if (showTooltip && isInside(mouseX, mouseY, x, y, 16, 16) && !stack.isEmpty()) {
                hoveredStack = stack;
            }
        }

        if (!hoveredStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredStack, mouseX, mouseY);
        }
    }

    static int getInventorySlotAt(double mouseX, double mouseY, int startX, int startY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int x = startX + MAIN_START_X + col * SLOT_STEP;
                int y = startY + MAIN_START_Y + row * SLOT_STEP;
                if (isInside(mouseX, mouseY, x, y, 16, 16)) {
                    return 9 + row * 9 + col;
                }
            }
        }

        for (int col = 0; col < 9; col++) {
            int x = startX + MAIN_START_X + col * SLOT_STEP;
            int y = startY + HOTBAR_Y;
            if (isInside(mouseX, mouseY, x, y, 16, 16)) {
                return col;
            }
        }

        return -1;
    }

    private static void renderStack(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        graphics.renderItem(stack, x, y);
        graphics.renderItemDecorations(font, stack, x, y);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
