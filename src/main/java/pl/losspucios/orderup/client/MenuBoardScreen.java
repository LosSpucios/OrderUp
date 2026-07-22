package pl.losspucios.orderup.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.losspucios.orderup.blockentity.MenuBoardBlockEntity;
import pl.losspucios.orderup.network.OrderUpNetworking;

import java.util.ArrayList;
import java.util.List;

public class MenuBoardScreen extends Screen {
    private OrderUpNetworking.MenuDataPayload data;
    private final List<ItemStack> menuStacks = new ArrayList<>();
    private ItemStack draggedStack = ItemStack.EMPTY;
    private int draggedInventorySlot = -1;

    public MenuBoardScreen(OrderUpNetworking.MenuDataPayload data) {
        super(Component.translatable("screen.orderup.menu"));
        applyPayload(data);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    public void applyPayload(OrderUpNetworking.MenuDataPayload payload) {
        this.data = payload;
        menuStacks.clear();
        for (String idString : payload.itemIds()) {
            if (idString.isBlank()) {
                menuStacks.add(ItemStack.EMPTY);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(idString);
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            menuStacks.add(item == null || item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item));
        }
        while (menuStacks.size() < MenuBoardBlockEntity.SLOT_COUNT) menuStacks.add(ItemStack.EMPTY);
    }

    public BlockPos getMenuPos() {
        return data.menuPos();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x55000000);
        int left = (width - 330) / 2;
        int top = (height - 250) / 2;

        graphics.fill(left, top, left + 330, top + 250, 0xFFD6B777);
        graphics.fill(left + 8, top + 8, left + 322, top + 242, 0xFFF7E9C9);
        graphics.drawCenteredString(font, Component.translatable("screen.orderup.menu"), left + 165, top + 17, 0xFF3F2D20);

        graphics.drawString(font, Component.translatable("screen.orderup.food"), left + 28, top + 43, 0xFF4B3321, false);
        for (int i = 0; i < 4; i++) renderMenuSlot(graphics, left + 28 + i * 64, top + 58, i, mouseX, mouseY);

        graphics.drawString(font, Component.translatable("screen.orderup.drinks"), left + 28, top + 106, 0xFF4B3321, false);
        for (int i = 0; i < 2; i++) renderMenuSlot(graphics, left + 28 + i * 64, top + 121, 4 + i, mouseX, mouseY);

        graphics.drawString(font, Component.translatable("screen.orderup.inventory_hint"), left + 28, top + 164, 0xFF6A5140, false);
        renderInventory(graphics, left + 28, top + 180, mouseX, mouseY);

        super.render(graphics, mouseX, mouseY, partialTick);
        if (!draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX - 8, mouseY - 8);
        }
    }

    private void renderMenuSlot(GuiGraphics graphics, int x, int y, int slot, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + 20
                && mouseY >= y && mouseY <= y + 20;

        int border = hovered && !draggedStack.isEmpty()
                ? 0xFFF0B43A
                : 0xFF8B684B;
        graphics.fill(x - 2, y - 2, x + 22, y + 22, border);
        graphics.fill(x, y, x + 20, y + 20, 0xFFEAD9B7);
        ItemStack stack = menuStacks.get(slot);
        if (!stack.isEmpty()) graphics.renderItem(stack, x + 2, y + 2);
        String price = data.prices().size() > slot ? "$" + data.prices().get(slot) : "$0";
        graphics.drawCenteredString(font, price, x + 10, y + 25, 0xFF3D713B);
        if (draggedStack.isEmpty()
                && mouseX >= x && mouseX <= x + 20
                && mouseY >= y && mouseY <= y + 20
                && !stack.isEmpty()) {
            graphics.renderTooltip(font, stack, mouseX, mouseY);
        }
    }

    private void renderInventory(GuiGraphics graphics, int startX, int startY, int mouseX, int mouseY) {
        if (minecraft == null || minecraft.player == null) return;
        for (int i = 0; i < 36; i++) {
            int col = i % 9;
            int row = i / 9;
            int x = startX + col * 30;
            int y = startY + row * 18;
            ItemStack stack = minecraft.player.getInventory().getItem(i);
            graphics.fill(x, y, x + 18, y + 18, 0x55785D45);
            if (!stack.isEmpty()) graphics.renderItem(stack, x + 1, y + 1);
            if (mouseX >= x && mouseX <= x + 18 && mouseY >= y && mouseY <= y + 18 && !stack.isEmpty()) {
                graphics.renderTooltip(font, stack, mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int menuSlot = getMenuSlotAt(mouseX, mouseY);

        // PPM usuwa item z menu.
        if (button == 1 && menuSlot >= 0) {
            PacketDistributor.sendToServer(
                    new OrderUpNetworking.SetMenuSlotPayload(
                            data.menuPos(),
                            menuSlot,
                            ""
                    )
            );

            return true;
        }

        // Przeciąganie działa tylko LPM.
        if (button != 0 || minecraft == null || minecraft.player == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        int inventorySlot = getInventorySlotAt(mouseX, mouseY);

        if (inventorySlot >= 0) {
            ItemStack stack = minecraft.player.getInventory().getItem(inventorySlot);

            if (!stack.isEmpty()) {
                draggedInventorySlot = inventorySlot;
                draggedStack = stack.copyWithCount(1);
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (button == 0 && !draggedStack.isEmpty()) {
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && !draggedStack.isEmpty()) {
            int targetSlot = getMenuSlotAt(mouseX, mouseY);

            if (targetSlot >= 0) {
                boolean valid = targetSlot < MenuBoardBlockEntity.FOOD_SLOTS
                        ? MenuBoardBlockEntity.isFood(draggedStack)
                        : MenuBoardBlockEntity.isDrink(draggedStack);

                if (valid && !isItemAlreadyInMenu(draggedStack, targetSlot)) {
                    String id = BuiltInRegistries.ITEM
                            .getKey(draggedStack.getItem())
                            .toString();

                    PacketDistributor.sendToServer(
                            new OrderUpNetworking.SetMenuSlotPayload(
                                    data.menuPos(),
                                    targetSlot,
                                    id
                            )
                    );
                }
            }

            draggedStack = ItemStack.EMPTY;
            draggedInventorySlot = -1;

            return true;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    private int getMenuSlotAt(double mouseX, double mouseY) {
        int left = (width - 330) / 2;
        int top = (height - 250) / 2;

        for (int slot = 0; slot < MenuBoardBlockEntity.SLOT_COUNT; slot++) {
            int x = slot < 4
                    ? left + 28 + slot * 64
                    : left + 28 + (slot - 4) * 64;

            int y = slot < 4
                    ? top + 58
                    : top + 121;

            if (mouseX >= x && mouseX <= x + 20
                    && mouseY >= y && mouseY <= y + 20) {
                return slot;
            }
        }

        return -1;
    }

    private int getInventorySlotAt(double mouseX, double mouseY) {
        int left = (width - 330) / 2;
        int top = (height - 250) / 2;

        int startX = left + 28;
        int startY = top + 180;

        for (int i = 0; i < 36; i++) {
            int x = startX + (i % 9) * 30;
            int y = startY + (i / 9) * 18;

            if (mouseX >= x && mouseX <= x + 18
                    && mouseY >= y && mouseY <= y + 18) {
                return i;
            }
        }

        return -1;
    }

    private boolean isItemAlreadyInMenu(ItemStack stack, int targetSlot) {
        for (int i = 0; i < menuStacks.size(); i++) {
            if (i == targetSlot) {
                continue;
            }

            ItemStack existing = menuStacks.get(i);

            if (!existing.isEmpty()
                    && existing.getItem() == stack.getItem()) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
