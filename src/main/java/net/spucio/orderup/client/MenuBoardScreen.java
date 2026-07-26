package net.spucio.orderup.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;
import net.spucio.orderup.network.OrderUpNetworking;

import java.util.ArrayList;
import java.util.List;

public class MenuBoardScreen extends Screen {
    private static final int GUI_WIDTH = 240;
    private static final int GUI_HEIGHT = 286;
    private static final int MENU_SLOT_SIZE = 22;
    private static final int INVENTORY_Y = 183;

    private OrderUpNetworking.MenuDataPayload data;
    private final List<ItemStack> menuStacks = new ArrayList<>();
    private ItemStack draggedStack = ItemStack.EMPTY;

    public MenuBoardScreen(OrderUpNetworking.MenuDataPayload data) {
        super(Component.translatable("screen.orderup.menu"));
        applyPayload(data);
    }

    /** Order Up deliberately renders no vanilla blurred world background. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public final void applyPayload(OrderUpNetworking.MenuDataPayload payload) {
        this.data = payload;
        menuStacks.clear();

        int count = Math.min(payload.itemIds().size(), MenuBoardBlockEntity.SLOT_COUNT);
        for (int i = 0; i < count; i++) {
            String idString = payload.itemIds().get(i);
            if (idString.isBlank()) {
                menuStacks.add(ItemStack.EMPTY);
                continue;
            }

            ResourceLocation id = ResourceLocation.tryParse(idString);
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            menuStacks.add(item == null || item == net.minecraft.world.item.Items.AIR
                    ? ItemStack.EMPTY
                    : new ItemStack(item));
        }

        while (menuStacks.size() < MenuBoardBlockEntity.SLOT_COUNT) {
            menuStacks.add(ItemStack.EMPTY);
        }
    }

    public BlockPos getMenuPos() {
        return data.menuPos();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);

        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;

        renderPanel(graphics, left, top);

        graphics.drawCenteredString(
                font,
                Component.literal("RESTAURANT MENU"),
                left + GUI_WIDTH / 2,
                top + 13,
                0xFFF9E7C6
        );

        graphics.drawString(font, Component.literal("Food"), left + 20, top + 39, 0xFF553824, false);
        for (int i = 0; i < MenuBoardBlockEntity.FOOD_SLOTS; i++) {
            renderMenuSlot(graphics, foodSlotX(left, i), top + 53, i, mouseX, mouseY);
        }

        graphics.drawString(font, Component.literal("Drinks"), left + 20, top + 96, 0xFF553824, false);
        for (int i = 0; i < MenuBoardBlockEntity.DRINK_SLOTS; i++) {
            renderMenuSlot(
                    graphics,
                    drinkSlotX(left, i),
                    top + 110,
                    MenuBoardBlockEntity.FOOD_SLOTS + i,
                    mouseX,
                    mouseY
            );
        }

        graphics.drawCenteredString(
                font,
                Component.literal("Drag to add  |  Right-click to clear"),
                left + GUI_WIDTH / 2,
                top + 154,
                0xFF6A5140
        );

        graphics.drawString(font, Component.literal("Inventory"), left + 36, top + 171, 0xFF553824, false);

        if (minecraft != null && minecraft.player != null) {
            int inventoryX = inventoryX(left);
            int inventoryY = top + INVENTORY_Y;
            VanillaInventoryPanel.render(
                    graphics,
                    font,
                    minecraft.player.getInventory(),
                    inventoryX,
                    inventoryY,
                    mouseX,
                    mouseY,
                    draggedStack.isEmpty()
            );
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (!draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX - 8, mouseY - 8);
            graphics.renderItemDecorations(font, draggedStack, mouseX - 8, mouseY - 8);
        }
    }

    private void renderPanel(GuiGraphics graphics, int left, int top) {
        // Dark wood frame, warm parchment body and a colored restaurant header.
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xFF4B2D1D);
        graphics.fill(left + 3, top + 3, left + GUI_WIDTH - 3, top + GUI_HEIGHT - 3, 0xFFD1A968);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFFF3E2BF);

        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + 31, 0xFF9C4F38);
        graphics.fill(left + 7, top + 30, left + GUI_WIDTH - 7, top + 33, 0xFF6E3325);

        graphics.fill(left + 14, top + 35, left + GUI_WIDTH - 14, top + 89, 0xFFE8D1A5);
        graphics.fill(left + 14, top + 92, left + GUI_WIDTH - 14, top + 145, 0xFFE8D1A5);
        graphics.fill(left + 14, top + 168, left + GUI_WIDTH - 14, top + 169, 0xFFB48C52);
    }

    private void renderMenuSlot(GuiGraphics graphics, int x, int y, int slot, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, MENU_SLOT_SIZE, MENU_SLOT_SIZE);
        boolean validType = !draggedStack.isEmpty() && isValidForSlot(draggedStack, slot);
        boolean duplicate = !draggedStack.isEmpty() && isItemAlreadyInMenu(draggedStack, slot);
        boolean validDrop = hovered && validType && !duplicate;
        boolean invalidDrop = hovered && !draggedStack.isEmpty() && !validDrop;

        int frame = validDrop
                ? 0xFF5D9C4B
                : invalidDrop
                ? 0xFFB84A3C
                : hovered
                ? 0xFFD0A044
                : 0xFF76503A;

        graphics.fill(x - 2, y - 2, x + MENU_SLOT_SIZE + 2, y + MENU_SLOT_SIZE + 2, frame);
        graphics.fill(x, y, x + MENU_SLOT_SIZE, y + MENU_SLOT_SIZE, 0xFFF2E0BC);
        graphics.fill(x + 2, y + 2, x + MENU_SLOT_SIZE - 2, y + MENU_SLOT_SIZE - 2, 0xFFD0B789);

        ItemStack stack = menuStacks.get(slot);
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x + 3, y + 3);
        }

        String price = data.prices().size() > slot ? "$" + data.prices().get(slot) : "$0";
        graphics.drawCenteredString(
                font,
                price,
                x + MENU_SLOT_SIZE / 2,
                y + MENU_SLOT_SIZE + 5,
                0xFF33713A
        );

        if (draggedStack.isEmpty() && hovered && !stack.isEmpty()) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int menuSlot = getMenuSlotAt(mouseX, mouseY);

        if (button == 1 && menuSlot >= 0) {
            PacketDistributor.sendToServer(
                    new OrderUpNetworking.SetMenuSlotPayload(data.menuPos(), menuSlot, "")
            );
            return true;
        }

        if (button != 0 || minecraft == null || minecraft.player == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;
        int inventorySlot = VanillaInventoryPanel.getInventorySlotAt(
                mouseX,
                mouseY,
                inventoryX(left),
                top + INVENTORY_Y
        );

        if (inventorySlot >= 0) {
            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);
            if (!stack.isEmpty()) {
                draggedStack = stack.copyWithCount(1);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && !draggedStack.isEmpty()) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !draggedStack.isEmpty()) {
            int targetSlot = getMenuSlotAt(mouseX, mouseY);

            if (targetSlot >= 0
                    && isValidForSlot(draggedStack, targetSlot)
                    && !isItemAlreadyInMenu(draggedStack, targetSlot)) {
                String id = BuiltInRegistries.ITEM.getKey(draggedStack.getItem()).toString();
                PacketDistributor.sendToServer(
                        new OrderUpNetworking.SetMenuSlotPayload(data.menuPos(), targetSlot, id)
                );
            }

            draggedStack = ItemStack.EMPTY;
            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        draggedStack = ItemStack.EMPTY;
        super.removed();
    }

    private int getMenuSlotAt(double mouseX, double mouseY) {
        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;

        for (int i = 0; i < MenuBoardBlockEntity.FOOD_SLOTS; i++) {
            int x = foodSlotX(left, i);
            int y = top + 53;
            if (isInside(mouseX, mouseY, x, y, MENU_SLOT_SIZE, MENU_SLOT_SIZE)) return i;
        }

        for (int i = 0; i < MenuBoardBlockEntity.DRINK_SLOTS; i++) {
            int x = drinkSlotX(left, i);
            int y = top + 110;
            if (isInside(mouseX, mouseY, x, y, MENU_SLOT_SIZE, MENU_SLOT_SIZE)) {
                return MenuBoardBlockEntity.FOOD_SLOTS + i;
            }
        }

        return -1;
    }

    private int inventoryX(int left) {
        return left + (GUI_WIDTH - VanillaInventoryPanel.WIDTH) / 2;
    }

    private int foodSlotX(int left, int index) {
        return left + 27 + index * 53;
    }

    private int drinkSlotX(int left, int index) {
        return left + 80 + index * 53;
    }

    private boolean isValidForSlot(ItemStack stack, int slot) {
        return slot < MenuBoardBlockEntity.FOOD_SLOTS
                ? MenuBoardBlockEntity.isFood(stack)
                : MenuBoardBlockEntity.isDrink(stack);
    }

    private boolean isItemAlreadyInMenu(ItemStack stack, int targetSlot) {
        for (int i = 0; i < menuStacks.size(); i++) {
            if (i == targetSlot) continue;

            ItemStack existing = menuStacks.get(i);
            if (!existing.isEmpty() && existing.getItem() == stack.getItem()) return true;
        }
        return false;
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
