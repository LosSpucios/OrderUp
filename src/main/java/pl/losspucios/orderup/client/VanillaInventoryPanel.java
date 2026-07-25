package pl.losspucios.orderup.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Small client-side helper that renders the player's inventory in the familiar
 * vanilla 9x3 + hotbar layout. The screen still uses ghost items, so rendering
 * this inventory never moves or consumes the real stacks.
 */
final class VanillaInventoryPanel {
    static final int SLOT_SIZE = 18;
    static final int WIDTH = 9 * SLOT_SIZE;
    static final int HOTBAR_GAP = 4;
    static final int HEIGHT = 3 * SLOT_SIZE + HOTBAR_GAP + SLOT_SIZE;

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
        ItemStack hoveredStack = ItemStack.EMPTY;

        // Vanilla main inventory: slots 9-35, three rows of nine.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int inventorySlot = 9 + row * 9 + col;
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                ItemStack stack = inventory.getItem(inventorySlot);
                renderSlot(graphics, font, stack, x, y);

                if (showTooltip && isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE) && !stack.isEmpty()) {
                    hoveredStack = stack;
                }
            }
        }

        // Vanilla hotbar: slots 0-8.
        int hotbarY = startY + 3 * SLOT_SIZE + HOTBAR_GAP;
        for (int col = 0; col < 9; col++) {
            int x = startX + col * SLOT_SIZE;
            ItemStack stack = inventory.getItem(col);
            renderSlot(graphics, font, stack, x, hotbarY);

            if (showTooltip && isInside(mouseX, mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE) && !stack.isEmpty()) {
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
                int x = startX + col * SLOT_SIZE;
                int y = startY + row * SLOT_SIZE;
                if (isInside(mouseX, mouseY, x, y, SLOT_SIZE, SLOT_SIZE)) {
                    return 9 + row * 9 + col;
                }
            }
        }

        int hotbarY = startY + 3 * SLOT_SIZE + HOTBAR_GAP;
        for (int col = 0; col < 9; col++) {
            int x = startX + col * SLOT_SIZE;
            if (isInside(mouseX, mouseY, x, hotbarY, SLOT_SIZE, SLOT_SIZE)) {
                return col;
            }
        }

        return -1;
    }

    private static void renderSlot(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        // Vanilla-ish recessed slot: light top/left, dark bottom/right, neutral center.
        graphics.fill(x, y, x + 18, y + 18, 0xFF373737);
        graphics.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + 18, y + 18, 0xFF555555);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);

        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
        }
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
