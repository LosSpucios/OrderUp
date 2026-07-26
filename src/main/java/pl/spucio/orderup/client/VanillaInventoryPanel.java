package pl.spucio.orderup.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the normal Minecraft player-inventory layout (3x9 plus hotbar), but
 * recolors its panel and slots to match Order Up's warm restaurant palette.
 * The stacks remain ghost-item drag sources; this helper never moves items.
 */
final class VanillaInventoryPanel {
    static final int WIDTH = 176;
    static final int HEIGHT = 96;

    private static final int MAIN_START_X = 8;
    private static final int MAIN_START_Y = 8;
    private static final int HOTBAR_Y = 71;
    private static final int SLOT_STEP = 18;

    private static final int FRAME_DARK = 0xFF68452F;
    private static final int FRAME_LIGHT = 0xFFD6B77D;
    private static final int PANEL = 0xFFE2C99A;
    private static final int SLOT_SHADOW = 0xFF76523A;
    private static final int SLOT_BODY = 0xFFC8AA78;
    private static final int SLOT_INNER = 0xFFE8D2A6;
    private static final int SLOT_HIGHLIGHT = 0xFFF7E6C1;

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
        renderPanel(graphics, startX, startY);
        ItemStack hoveredStack = ItemStack.EMPTY;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventorySlot = 9 + row * 9 + col;
                int x = startX + MAIN_START_X + col * SLOT_STEP;
                int y = startY + MAIN_START_Y + row * SLOT_STEP;
                renderSlot(graphics, x, y);

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
            renderSlot(graphics, x, y);

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

    private static void renderPanel(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, FRAME_DARK);
        graphics.fill(x + 2, y + 2, x + WIDTH - 2, y + HEIGHT - 2, FRAME_LIGHT);
        graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + HEIGHT - 4, PANEL);
    }

    private static void renderSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADOW);
        graphics.fill(x, y, x + 16, y + 16, SLOT_BODY);
        graphics.fill(x + 1, y + 1, x + 15, y + 15, SLOT_INNER);
        graphics.fill(x + 1, y + 1, x + 15, y + 2, SLOT_HIGHLIGHT);
        graphics.fill(x + 1, y + 1, x + 2, y + 15, SLOT_HIGHLIGHT);
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
