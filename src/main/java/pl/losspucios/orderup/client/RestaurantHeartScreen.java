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
    private static final int MIN_GUI_HEIGHT = 194;
    private static final int OWNER_ROW_Y = 116;
    private static final int MEMBER_ROWS_Y = 140;
    private static final int ROW_STEP = 22;

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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No vanilla blur. The screen draws only a translucent dark overlay.
    }

    @Override
    protected void init() {
        restaurantNameBox = new EditBox(
                font,
                0,
                0,
                226,
                20,
                Component.translatable("screen.orderup.restaurant_name")
        );
        restaurantNameBox.setValue(data.name());
        restaurantNameBox.setMaxLength(32);
        restaurantNameBox.setEditable(isLocalPlayerOwner());
        addRenderableWidget(restaurantNameBox);

        saveNameButton = Button.builder(Component.literal("✓"), button ->
                        PacketDistributor.sendToServer(
                                new OrderUpNetworking.RenameRestaurantPayload(
                                        data.heartPos(),
                                        restaurantNameBox.getValue()
                                )
                        ))
                .bounds(0, 0, 30, 20)
                .build();
        addRenderableWidget(saveNameButton);

        addButton = Button.builder(Component.literal("+"), button -> setAddMode(true))
                .bounds(0, 0, 20, 20)
                .build();
        addRenderableWidget(addButton);

        addMemberBox = new EditBox(
                font,
                0,
                0,
                140,
                20,
                Component.translatable("screen.orderup.player_name")
        );
        addMemberBox.setMaxLength(16);
        addRenderableWidget(addMemberBox);

        confirmAddButton = Button.builder(Component.literal("✓"), button -> confirmAddMember())
                .bounds(0, 0, 34, 20)
                .build();
        addRenderableWidget(confirmAddButton);

        cancelAddButton = Button.builder(Component.literal("✕"), button -> setAddMode(false))
                .bounds(0, 0, 34, 20)
                .build();
        addRenderableWidget(cancelAddButton);

        repositionWidgets();
        updateWidgetVisibility();
    }

    private void repositionWidgets() {
        int left = getLeft();
        int top = getTop();
        int addRow = top + getAddRowY();

        restaurantNameBox.setX(left + 18);
        restaurantNameBox.setY(top + 15);
        saveNameButton.setX(left + 250);
        saveNameButton.setY(top + 15);

        addButton.setX(left + 28);
        addButton.setY(addRow);
        addMemberBox.setX(left + 54);
        addMemberBox.setY(addRow);
        confirmAddButton.setX(left + 200);
        confirmAddButton.setY(addRow);
        cancelAddButton.setX(left + 240);
        cancelAddButton.setY(addRow);
    }

    private void updateWidgetVisibility() {
        boolean owner = isLocalPlayerOwner();
        restaurantNameBox.setEditable(owner);
        saveNameButton.visible = owner;
        addButton.visible = owner && !addMode;
        addMemberBox.visible = owner && addMode;
        confirmAddButton.visible = owner && addMode;
        cancelAddButton.visible = owner && addMode;
    }

    private void setAddMode(boolean value) {
        addMode = value && isLocalPlayerOwner();
        updateWidgetVisibility();

        if (addMode) {
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
        if (addButton != null) {
            repositionWidgets();
            updateWidgetVisibility();
        }
    }

    public BlockPos getHeartPos() {
        return data.heartPos();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x66000000);

        int left = getLeft();
        int top = getTop();
        renderPanel(graphics, left, top);
        renderRestaurantProgress(graphics, left, top);
        renderCrew(graphics, left, top);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int left, int top) {
        int guiHeight = getGuiHeight();
        graphics.fill(left, top, left + GUI_WIDTH, top + guiHeight, 0xFF4B2D1D);
        graphics.fill(left + 3, top + 3, left + GUI_WIDTH - 3, top + guiHeight - 3, 0xFFD1A968);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + guiHeight - 7, 0xFFF3E2BF);

        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + 41, 0xFF8F4935);
        graphics.fill(left + 7, top + 39, left + GUI_WIDTH - 7, top + 42, 0xFF633124);
        graphics.fill(left + 16, top + 47, left + GUI_WIDTH - 16, top + 92, 0xFFE5CFA3);
        graphics.fill(left + 16, top + 96, left + GUI_WIDTH - 16, top + guiHeight - 12, 0xFFE9D7B4);
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
        int barWidth = 214;
        int barHeight = 10;
        graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF5B4938);
        graphics.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + barHeight - 1, 0xFF8B765D);

        double progress = Math.min(1.0D, data.xp() / (double) Math.max(1, data.nextXp()));
        int fill = (int) Math.floor((barWidth - 2) * progress);
        if (fill > 0) {
            graphics.fill(barX + 1, barY + 1, barX + 1 + fill, barY + barHeight - 1, 0xFF72AD58);
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
        graphics.drawString(
                font,
                moneyText,
                left + 272 - font.width(moneyText),
                top + 101,
                0xFF3D873F,
                false
        );

        renderPlayerFace(graphics, data.ownerId(), data.ownerName(), left + 28, top + OWNER_ROW_Y, 18);
        graphics.drawString(font, data.ownerName(), left + 53, top + OWNER_ROW_Y + 5, 0xFF3B2A1D, false);
        graphics.drawString(font, Component.literal("Founder"), left + 215, top + OWNER_ROW_Y + 5, 0xFF8B623E, false);

        List<OrderUpNetworking.MemberData> members = nonOwnerMembers();
        for (int i = 0; i < members.size(); i++) {
            OrderUpNetworking.MemberData member = members.get(i);
            int rowY = top + MEMBER_ROWS_Y + i * ROW_STEP;
            renderPlayerFace(graphics, member.id(), member.name(), left + 28, rowY, 18);
            graphics.drawString(font, member.name(), left + 53, rowY + 5, 0xFF3B2A1D, false);

            if (isLocalPlayerOwner()) {
                graphics.drawString(font, "✕", left + 255, rowY + 5, 0xFF9D2F2F, false);
            }
        }

        if (addMode) {
            int addRow = top + getAddRowY();
            graphics.fill(left + 28, addRow, left + 48, addRow + 20, 0xFF191919);
            graphics.drawCenteredString(font, "?", left + 38, addRow + 6, 0xFFFFFFFF);
        }
    }

    private List<OrderUpNetworking.MemberData> nonOwnerMembers() {
        List<OrderUpNetworking.MemberData> members = new ArrayList<>();
        for (OrderUpNetworking.MemberData member : data.members()) {
            if (!member.id().equals(data.ownerId())) {
                members.add(member);
            }
        }
        members.sort(Comparator.comparing(OrderUpNetworking.MemberData::name, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    private void renderPlayerFace(GuiGraphics graphics, UUID id, String name, int x, int y, int size) {
        try {
            GameProfile profile = new GameProfile(id, name);
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            PlayerFaceRenderer.draw(graphics, skin.texture(), x, y, size);
        } catch (RuntimeException ignored) {
            graphics.fill(x, y, x + size, y + size, 0xFF5F4739);
            String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            graphics.drawCenteredString(font, initial, x + size / 2, y + 5, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isLocalPlayerOwner() && !addMode) {
            int left = getLeft();
            int top = getTop();
            List<OrderUpNetworking.MemberData> members = nonOwnerMembers();

            for (int i = 0; i < members.size(); i++) {
                OrderUpNetworking.MemberData member = members.get(i);
                int rowY = top + MEMBER_ROWS_Y + i * ROW_STEP;
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

    private int getGuiHeight() {
        return Math.max(MIN_GUI_HEIGHT, getAddRowY() + 32);
    }

    private int getAddRowY() {
        return MEMBER_ROWS_Y + nonOwnerMembers().size() * ROW_STEP;
    }

    private int getLeft() {
        return (width - GUI_WIDTH) / 2;
    }

    private int getTop() {
        return (height - getGuiHeight()) / 2;
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
