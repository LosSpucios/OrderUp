package pl.losspucios.orderup.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import pl.losspucios.orderup.network.OrderUpNetworking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class RestaurantHeartScreen extends Screen {
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 322;
    private static final int OWNER_ROW_Y = 116;
    private static final int ADD_ROW_Y = 139;
    private static final int MEMBER_ROWS_Y = 162;
    private static final int INVENTORY_Y = 222;

    private OrderUpNetworking.HeartDataPayload data;
    private EditBox restaurantNameBox;
    private EditBox addMemberBox;
    private Button addButton;
    private Button confirmAddButton;
    private Button cancelAddButton;
    private Button saveNameButton;
    private boolean addMode;

    public RestaurantHeartScreen(OrderUpNetworking.HeartDataPayload data) {
        super(Component.translatable("screen.orderup.restaurant_heart"));
        this.data = data;
    }

    /** Prevent vanilla from adding a blurred world layer behind this screen. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    protected void init() {
        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;
        boolean owner = isLocalPlayerOwner();

        restaurantNameBox = new EditBox(
                font,
                left + 18,
                top + 15,
                226,
                20,
                Component.translatable("screen.orderup.restaurant_name")
        );
        restaurantNameBox.setValue(data.name());
        restaurantNameBox.setMaxLength(32);
        restaurantNameBox.setEditable(owner);
        addRenderableWidget(restaurantNameBox);

        saveNameButton = Button.builder(Component.literal("✓"), button ->
                        PacketDistributor.sendToServer(
                                new OrderUpNetworking.RenameRestaurantPayload(
                                        data.heartPos(),
                                        restaurantNameBox.getValue()
                                )
                        ))
                .bounds(left + 250, top + 15, 30, 20)
                .build();
        saveNameButton.visible = owner;
        addRenderableWidget(saveNameButton);

        // The plus is always the row immediately below the founder.
        addButton = Button.builder(Component.literal("+"), button -> setAddMode(true))
                .bounds(left + 28, top + ADD_ROW_Y, 20, 20)
                .build();
        addButton.visible = owner;
        addRenderableWidget(addButton);

        addMemberBox = new EditBox(
                font,
                left + 54,
                top + ADD_ROW_Y,
                140,
                20,
                Component.translatable("screen.orderup.player_name")
        );
        addMemberBox.setMaxLength(16);
        addMemberBox.visible = false;
        addRenderableWidget(addMemberBox);

        confirmAddButton = Button.builder(Component.literal("✓"), button -> confirmAddMember())
                .bounds(left + 200, top + ADD_ROW_Y, 34, 20)
                .build();
        confirmAddButton.visible = false;
        addRenderableWidget(confirmAddButton);

        cancelAddButton = Button.builder(Component.literal("✕"), button -> setAddMode(false))
                .bounds(left + 240, top + ADD_ROW_Y, 34, 20)
                .build();
        cancelAddButton.visible = false;
        addRenderableWidget(cancelAddButton);
    }

    private void setAddMode(boolean value) {
        addMode = value;
        addButton.visible = !value && isLocalPlayerOwner();
        addMemberBox.visible = value;
        confirmAddButton.visible = value;
        cancelAddButton.visible = value;

        if (value) {
            setInitialFocus(addMemberBox);
        } else {
            addMemberBox.setValue("");
        }
    }

    private void confirmAddMember() {
        String name = addMemberBox.getValue().strip();
        if (!name.isBlank()) {
            PacketDistributor.sendToServer(
                    new OrderUpNetworking.AddMemberPayload(data.heartPos(), name)
            );
        }
        setAddMode(false);
    }

    public void applyPayload(OrderUpNetworking.HeartDataPayload payload) {
        this.data = payload;
        if (restaurantNameBox != null && !restaurantNameBox.isFocused()) {
            restaurantNameBox.setValue(payload.name());
        }
    }

    public BlockPos getHeartPos() {
        return data.heartPos();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);

        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;

        renderPanel(graphics, left, top);
        renderRestaurantProgress(graphics, left, top);
        renderCrew(graphics, left, top);

        graphics.drawString(font, Component.literal("Inventory"), left + 69, top + 214, 0xFF553824, false);

        if (minecraft != null && minecraft.player != null) {
            int inventoryX = left + (GUI_WIDTH - VanillaInventoryPanel.WIDTH) / 2;
            VanillaInventoryPanel.render(
                    graphics,
                    font,
                    minecraft.player.getInventory(),
                    inventoryX,
                    top + INVENTORY_Y,
                    mouseX,
                    mouseY,
                    true
            );
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xFF4B2D1D);
        graphics.fill(left + 3, top + 3, left + GUI_WIDTH - 3, top + GUI_HEIGHT - 3, 0xFFD1A968);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFFF3E2BF);

        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + 41, 0xFF8F4935);
        graphics.fill(left + 7, top + 39, left + GUI_WIDTH - 7, top + 42, 0xFF633124);

        graphics.fill(left + 16, top + 47, left + GUI_WIDTH - 16, top + 92, 0xFFE5CFA3);
        graphics.fill(left + 16, top + 96, left + GUI_WIDTH - 16, top + 207, 0xFFE9D7B4);
        graphics.fill(left + 14, top + 210, left + GUI_WIDTH - 14, top + 211, 0xFFB48C52);
    }

    private void renderRestaurantProgress(GuiGraphics graphics, int left, int top) {
        graphics.drawCenteredString(
                font,
                Component.literal("Restaurant Level " + data.level()),
                left + GUI_WIDTH / 2,
                top + 52,
                0xFF4A2D18
        );

        int barX = left + 43;
        int barY = top + 68;
        int barW = 214;
        int barH = 10;

        graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF5B4938);
        graphics.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF8B765D);

        double progress = Math.min(1.0D, data.xp() / (double) Math.max(1, data.nextXp()));
        int fill = (int) Math.floor((barW - 2) * progress);
        if (fill > 0) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + fill, barY + barH - 1, 0xFF72AD58);
        }

        graphics.drawCenteredString(
                font,
                data.xp() + " / " + data.nextXp() + " XP",
                left + GUI_WIDTH / 2,
                top + 81,
                0xFF59412D
        );
    }

    private void renderCrew(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, Component.literal("Crew"), left + 27, top + 101, 0xFF4A2D18, false);

        String moneyText = "$" + data.money();
        int moneyWidth = font.width(moneyText);
        graphics.drawString(font, moneyText, left + 272 - moneyWidth, top + 101, 0xFF3D873F, false);

        renderPlayerFace(graphics, data.ownerId(), data.ownerName(), left + 28, top + OWNER_ROW_Y, 18);
        graphics.drawString(font, data.ownerName(), left + 53, top + OWNER_ROW_Y + 5, 0xFF3B2A1D, false);
        graphics.drawString(font, Component.literal("Founder"), left + 215, top + OWNER_ROW_Y + 5, 0xFF8B623E, false);

        if (addMode) {
            graphics.fill(left + 28, top + ADD_ROW_Y, left + 48, top + ADD_ROW_Y + 20, 0xFF191919);
            graphics.drawCenteredString(font, "?", left + 38, top + ADD_ROW_Y + 6, 0xFFFFFFFF);
        }

        List<OrderUpNetworking.MemberData> members = nonOwnerMembers();
        int visibleRows = Math.min(2, members.size());

        for (int i = 0; i < visibleRows; i++) {
            OrderUpNetworking.MemberData member = members.get(i);
            int rowY = top + MEMBER_ROWS_Y + i * 22;

            renderPlayerFace(graphics, member.id(), member.name(), left + 28, rowY, 18);
            graphics.drawString(font, member.name(), left + 53, rowY + 5, 0xFF3B2A1D, false);

            if (isLocalPlayerOwner()) {
                graphics.drawString(font, "✕", left + 255, rowY + 5, 0xFF9D2F2F, false);
            }
        }

        if (members.size() > visibleRows) {
            graphics.drawString(
                    font,
                    "+" + (members.size() - visibleRows) + " more",
                    left + 200,
                    top + 198,
                    0xFF6A5646,
                    false
            );
        }
    }

    private List<OrderUpNetworking.MemberData> nonOwnerMembers() {
        List<OrderUpNetworking.MemberData> members = new ArrayList<>();
        for (OrderUpNetworking.MemberData member : data.members()) {
            if (!member.id().equals(data.ownerId())) members.add(member);
        }
        members.sort(Comparator.comparing(OrderUpNetworking.MemberData::name, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    private void renderPlayerFace(GuiGraphics graphics, UUID id, String name, int x, int y, int size) {
        try {
            GameProfile profile = new GameProfile(id, name);
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            PlayerFaceRenderer.draw(graphics, skin.texture(), x, y, size);
        } catch (Exception ignored) {
            graphics.fill(x, y, x + size, y + size, 0xFF5F4739);
            String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            graphics.drawCenteredString(font, initial, x + size / 2, y + 5, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isLocalPlayerOwner() && !addMode) {
            int left = (width - GUI_WIDTH) / 2;
            int top = (height - GUI_HEIGHT) / 2;
            List<OrderUpNetworking.MemberData> members = nonOwnerMembers();

            for (int i = 0; i < Math.min(2, members.size()); i++) {
                OrderUpNetworking.MemberData member = members.get(i);
                int rowY = top + MEMBER_ROWS_Y + i * 22;

                if (mouseX >= left + 246 && mouseX <= left + 278
                        && mouseY >= rowY && mouseY <= rowY + 18) {
                    PacketDistributor.sendToServer(
                            new OrderUpNetworking.RemoveMemberPayload(data.heartPos(), member.id())
                    );
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isLocalPlayerOwner() {
        return minecraft != null
                && minecraft.player != null
                && minecraft.player.getUUID().equals(data.ownerId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
