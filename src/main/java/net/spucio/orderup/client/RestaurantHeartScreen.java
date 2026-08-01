package net.spucio.orderup.client;

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
import net.spucio.orderup.network.OrderUpNetworking;
import net.spucio.orderup.util.MoneyFormatter;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class RestaurantHeartScreen extends Screen {
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 220;
    private static final int OWNER_ROW_Y = 116;
    private static final int MEMBER_ROWS_Y = 140;
    private static final int VISIBLE_MEMBER_ROWS = 2;
    private static final int ROW_STEP = 22;
    private static final int MEMBER_VIEW_HEIGHT = VISIBLE_MEMBER_ROWS * ROW_STEP;

    private OrderUpNetworking.HeartDataPayload data;
    private EditBox restaurantNameBox;
    private EditBox addMemberBox;
    private Button addButton;
    private Button confirmAddButton;
    private Button cancelAddButton;
    private boolean addMode;
    private int memberScrollOffset;

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
                240,
                18,
                Component.translatable("screen.orderup.restaurant_name")
        );
        restaurantNameBox.setValue(data.name());
        restaurantNameBox.setMaxLength(32);
        restaurantNameBox.setBordered(false);
        restaurantNameBox.setTextColor(0xFFFFFFFF);
        restaurantNameBox.setTextColorUneditable(0xFFD8D8D8);
        restaurantNameBox.setEditable(isLocalPlayerOwner());
        addRenderableWidget(restaurantNameBox);

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

        clampMemberScroll();
        repositionWidgets();
        updateWidgetVisibility();
    }

    private void repositionWidgets() {
        int left = getLeft();
        int top = getTop();
        int addRow = top + getAddRowY();

        repositionRestaurantNameBox(left, top);

        addButton.setX(left + 28);
        addButton.setY(addRow);
        addMemberBox.setX(left + 54);
        addMemberBox.setY(addRow);
        confirmAddButton.setX(left + 200);
        confirmAddButton.setY(addRow);
        cancelAddButton.setX(left + 240);
        cancelAddButton.setY(addRow);
    }

    private void repositionRestaurantNameBox(int left, int top) {
        if (restaurantNameBox == null) return;

        int maxWidth = GUI_WIDTH - 52;
        int textWidth = font.width(restaurantNameBox.getValue()) + 8;
        int fieldWidth = Math.max(24, Math.min(maxWidth, textWidth));
        restaurantNameBox.setWidth(fieldWidth);
        restaurantNameBox.setX(left + GUI_WIDTH / 2 - fieldWidth / 2);
        restaurantNameBox.setY(top + 16);
    }

    private void updateWidgetVisibility() {
        boolean owner = isLocalPlayerOwner();
        restaurantNameBox.setEditable(owner);
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
        clampMemberScroll();
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
        repositionRestaurantNameBox(left, top);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xFF4B2D1D);
        graphics.fill(left + 3, top + 3, left + GUI_WIDTH - 3, top + GUI_HEIGHT - 3, 0xFFD1A968);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFFF3E2BF);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + 41, 0xFF8F4935);
        graphics.fill(left + 7, top + 39, left + GUI_WIDTH - 7, top + 42, 0xFF633124);

        // Moved up so it sits directly beneath the restaurant name.
        graphics.fill(left + 26, top + 32, left + GUI_WIDTH - 26, top + 33, 0xFFFFFFFF);

        graphics.fill(left + 16, top + 47, left + GUI_WIDTH - 16, top + 92, 0xFFE5CFA3);
        graphics.fill(left + 16, top + 96, left + GUI_WIDTH - 16, top + GUI_HEIGHT - 12, 0xFFE9D7B4);
    }

    private void renderRestaurantProgress(GuiGraphics graphics, int left, int top) {
        drawCenteredWhiteShadow(
                graphics,
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

        drawCenteredWhiteShadow(
                graphics,
                data.xp() + " / " + data.nextXp() + " XP",
                left + GUI_WIDTH / 2,
                top + 81,
                0xFF59412D
        );
    }

    private void renderCrew(GuiGraphics graphics, int left, int top) {
        drawWhiteShadow(graphics, Component.literal("Crew"), left + 27, top + 101, 0xFF4A2D18);
        String moneyText = MoneyFormatter.withDollarPrefix(data.money());
        drawWhiteShadow(
                graphics,
                moneyText,
                left + 272 - font.width(moneyText),
                top + 101,
                0xFF3D873F
        );

        renderPlayerFace(graphics, data.ownerId(), data.ownerName(), left + 28, top + OWNER_ROW_Y, 18);
        drawWhiteShadow(graphics, data.ownerName(), left + 53, top + OWNER_ROW_Y + 5, 0xFF3B2A1D);
        drawWhiteShadow(graphics, Component.literal("Founder"), left + 215, top + OWNER_ROW_Y + 5, 0xFF8B623E);

        List<OrderUpNetworking.MemberData> members = nonOwnerMembers();
        clampMemberScroll(members.size());

        int listTop = top + MEMBER_ROWS_Y;
        int listBottom = listTop + MEMBER_VIEW_HEIGHT;
        graphics.enableScissor(left + 20, listTop, left + GUI_WIDTH - 17, listBottom);
        int visibleEnd = Math.min(members.size(), memberScrollOffset + VISIBLE_MEMBER_ROWS);
        for (int memberIndex = memberScrollOffset; memberIndex < visibleEnd; memberIndex++) {
            OrderUpNetworking.MemberData member = members.get(memberIndex);
            int visibleIndex = memberIndex - memberScrollOffset;
            int rowY = listTop + visibleIndex * ROW_STEP;
            renderPlayerFace(graphics, member.id(), member.name(), left + 28, rowY, 18);
            drawWhiteShadow(graphics, member.name(), left + 53, rowY + 5, 0xFF3B2A1D);
            if (isLocalPlayerOwner()) {
                drawWhiteShadow(graphics, "✕", left + 255, rowY + 5, 0xFF9D2F2F);
            }
        }
        graphics.disableScissor();

        renderMemberScrollbar(graphics, left, top, members.size());

        if (addMode) {
            int addRow = top + getAddRowY();
            graphics.fill(left + 28, addRow, left + 48, addRow + 20, 0xFF191919);

            // Deliberately no shadow on the question mark.
            String questionMark = "?";
            int questionX = left + 38 - font.width(questionMark) / 2;
            graphics.drawString(font, questionMark, questionX, addRow + 6, 0xFFFFFFFF, false);
        }
    }

    private void renderMemberScrollbar(GuiGraphics graphics, int left, int top, int memberCount) {
        int maxScroll = Math.max(0, memberCount - VISIBLE_MEMBER_ROWS);
        if (maxScroll == 0) return;

        int trackY = top + MEMBER_ROWS_Y;
        int trackHeight = MEMBER_VIEW_HEIGHT;
        int thumbHeight = Math.max(6, trackHeight / memberCount);
        int thumbTravel = trackHeight - thumbHeight;
        int thumbY = trackY + (int) Math.round(thumbTravel * (memberScrollOffset / (double) maxScroll));

        // Brown thumb only: no bright/white vertical track at the right edge.
        graphics.fill(left + 280, thumbY, left + 283, thumbY + thumbHeight, 0xFF8B623E);
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

    private void clampMemberScroll() {
        clampMemberScroll(nonOwnerMembers().size());
    }

    private void clampMemberScroll(int memberCount) {
        int maxScroll = Math.max(0, memberCount - VISIBLE_MEMBER_ROWS);
        memberScrollOffset = Math.max(0, Math.min(memberScrollOffset, maxScroll));
    }

    private void renderPlayerFace(GuiGraphics graphics, UUID id, String name, int x, int y, int size) {
        try {
            GameProfile profile = new GameProfile(id, name);
            PlayerSkin skin = Minecraft.getInstance().getSkinManager().getInsecureSkin(profile);
            PlayerFaceRenderer.draw(graphics, skin.texture(), x, y, size);
        } catch (RuntimeException ignored) {
            graphics.fill(x, y, x + size, y + size, 0xFF5F4739);
            String initial = name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
            drawCenteredWhiteShadow(graphics, initial, x + size / 2, y + 5, 0xFFFFFFFF);
        }
    }

    private void submitRestaurantName() {
        if (!isLocalPlayerOwner() || restaurantNameBox == null) return;

        String name = restaurantNameBox.getValue().strip();
        if (name.isBlank()) {
            name = "My Restaurant";
            restaurantNameBox.setValue(name);
        }

        PacketDistributor.sendToServer(
                new OrderUpNetworking.RenameRestaurantPayload(data.heartPos(), name)
        );
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (restaurantNameBox != null
                && restaurantNameBox.isFocused()
                && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            submitRestaurantName();
            restaurantNameBox.setFocused(false);
            return true;
        }

        boolean typingInTextBox = (restaurantNameBox != null && restaurantNameBox.isFocused())
                || (addMemberBox != null && addMemberBox.isFocused());
        if (!typingInTextBox
                && minecraft != null
                && minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int left = getLeft();
        int top = getTop();

        if (button == 0
                && isLocalPlayerOwner()
                && mouseX >= left + 26
                && mouseX <= left + GUI_WIDTH - 26
                && mouseY >= top + 11
                && mouseY <= top + 37) {
            setFocused(restaurantNameBox);
            restaurantNameBox.setFocused(true);
            return true;
        }

        if (button == 0 && isLocalPlayerOwner() && !addMode) {
            List<OrderUpNetworking.MemberData> members = nonOwnerMembers();
            clampMemberScroll(members.size());

            int visibleEnd = Math.min(members.size(), memberScrollOffset + VISIBLE_MEMBER_ROWS);
            for (int memberIndex = memberScrollOffset; memberIndex < visibleEnd; memberIndex++) {
                int visibleIndex = memberIndex - memberScrollOffset;
                int rowY = top + MEMBER_ROWS_Y + visibleIndex * ROW_STEP;
                if (mouseX >= left + 246 && mouseX <= left + 278
                        && mouseY >= rowY && mouseY <= rowY + 18) {
                    OrderUpNetworking.MemberData member = members.get(memberIndex);
                    PacketDistributor.sendToServer(
                            new OrderUpNetworking.RemoveMemberPayload(data.heartPos(), member.id())
                    );
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int left = getLeft();
        int top = getTop();
        boolean overMemberList = mouseX >= left + 20
                && mouseX <= left + GUI_WIDTH - 17
                && mouseY >= top + MEMBER_ROWS_Y
                && mouseY < top + MEMBER_ROWS_Y + MEMBER_VIEW_HEIGHT;

        if (overMemberList && scrollY != 0.0D) {
            int maxScroll = Math.max(0, nonOwnerMembers().size() - VISIBLE_MEMBER_ROWS);
            int direction = scrollY > 0.0D ? -1 : 1;
            int nextOffset = Math.max(0, Math.min(memberScrollOffset + direction, maxScroll));
            if (nextOffset != memberScrollOffset) {
                memberScrollOffset = nextOffset;
                return true;
            }
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private int getAddRowY() {
        int visibleMemberCount = Math.min(nonOwnerMembers().size(), VISIBLE_MEMBER_ROWS);
        return MEMBER_ROWS_Y + visibleMemberCount * ROW_STEP;
    }

    private int getLeft() {
        return (width - GUI_WIDTH) / 2;
    }

    private int getTop() {
        return (height - GUI_HEIGHT) / 2;
    }

    private boolean isLocalPlayerOwner() {
        return minecraft != null
                && minecraft.player != null
                && minecraft.player.getUUID().equals(data.ownerId());
    }

    private void drawWhiteShadow(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x + 1, y + 1, 0xFFFFFFFF, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private void drawWhiteShadow(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(font, text, x + 1, y + 1, 0xFFFFFFFF, false);
        graphics.drawString(font, text, x, y, color, false);
    }

    private void drawCenteredWhiteShadow(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        drawWhiteShadow(graphics, text, x, y, color);
    }

    private void drawCenteredWhiteShadow(GuiGraphics graphics, String text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        drawWhiteShadow(graphics, text, x, y, color);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
