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
import java.util.List;
import java.util.UUID;

public class RestaurantHeartScreen extends Screen {
    private static final int GUI_WIDTH = 300;
    private static final int GUI_HEIGHT = 312;

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

    /** Prevent vanilla from submitting the blurred background layer. */
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
                top + 17,
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
                                new OrderUpNetworking.RenameRestaurantPayload(data.heartPos(), restaurantNameBox.getValue())
                        ))
                .bounds(left + 250, top + 17, 30, 20)
                .build();
        saveNameButton.visible = owner;
        addRenderableWidget(saveNameButton);

        // The add button sits next to the Crew heading rather than floating at the bottom.
        addButton = Button.builder(Component.literal("+"), button -> setAddMode(true))
                .bounds(left + 252, top + 103, 26, 20)
                .build();
        addButton.visible = owner;
        addRenderableWidget(addButton);

        addMemberBox = new EditBox(
                font,
                left + 70,
                top + 165,
                132,
                20,
                Component.translatable("screen.orderup.player_name")
        );
        addMemberBox.setMaxLength(16);
        addMemberBox.visible = false;
        addRenderableWidget(addMemberBox);

        confirmAddButton = Button.builder(Component.literal("✓"), button -> confirmAddMember())
                .bounds(left + 207, top + 165, 32, 20)
                .build();
        confirmAddButton.visible = false;
        addRenderableWidget(confirmAddButton);

        cancelAddButton = Button.builder(Component.literal("✕"), button -> setAddMode(false))
                .bounds(left + 244, top + 165, 32, 20)
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
            PacketDistributor.sendToServer(new OrderUpNetworking.AddMemberPayload(data.heartPos(), name));
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
        // Simple darkening without the vanilla world blur.
        graphics.fill(0, 0, width, height, 0x66000000);

        int left = (width - GUI_WIDTH) / 2;
        int top = (height - GUI_HEIGHT) / 2;

        renderPanel(graphics, left, top);
        renderRestaurantProgress(graphics, left, top);
        renderCrew(graphics, left, top);

        graphics.fill(left + 12, top + 197, left + GUI_WIDTH - 12, top + 198, 0xFFB99A68);
        graphics.drawString(font, Component.literal("Inventory"), left + 69, top + 205, 0xFF553824, false);

        if (minecraft != null && minecraft.player != null) {
            int inventoryX = left + (GUI_WIDTH - VanillaInventoryPanel.WIDTH) / 2;
            int inventoryY = top + 218;
            VanillaInventoryPanel.render(
                    graphics,
                    font,
                    minecraft.player.getInventory(),
                    inventoryX,
                    inventoryY,
                    mouseX,
                    mouseY,
                    true
            );
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphics graphics, int left, int top) {
        graphics.fill(left, top, left + GUI_WIDTH, top + GUI_HEIGHT, 0xFF5A3823);
        graphics.fill(left + 3, top + 3, left + GUI_WIDTH - 3, top + GUI_HEIGHT - 3, 0xFFD7B77B);
        graphics.fill(left + 7, top + 7, left + GUI_WIDTH - 7, top + GUI_HEIGHT - 7, 0xFFF4E5C5);

        // Header shadow and two soft cards make the layout easier to read.
        graphics.fill(left + 14, top + 43, left + GUI_WIDTH - 14, top + 45, 0xFFB58C53);
        graphics.fill(left + 16, top + 51, left + GUI_WIDTH - 16, top + 96, 0xFFE7D2A8);
        graphics.fill(left + 16, top + 100, left + GUI_WIDTH - 16, top + 192, 0xFFE9D8B7);
    }

    private void renderRestaurantProgress(GuiGraphics graphics, int left, int top) {
        graphics.drawCenteredString(
                font,
                Component.literal("Restaurant Level " + data.level()),
                left + GUI_WIDTH / 2,
                top + 57,
                0xFF4A2D18
        );

        int barX = left + 43;
        int barY = top + 73;
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
                top + 85,
                0xFF59412D
        );
    }

    private void renderCrew(GuiGraphics graphics, int left, int top) {
        graphics.drawString(font, Component.literal("Crew"), left + 27, top + 108, 0xFF4A2D18, false);

        String moneyText = "$" + data.money();
        int moneyWidth = font.width(moneyText);
        graphics.drawString(font, moneyText, left + 238 - moneyWidth, top + 109, 0xFF3D873F, false);

        List<OrderUpNetworking.MemberData> members = new ArrayList<>(data.members());
        int maxRows = addMode ? Math.min(2, members.size()) : Math.min(3, members.size());

        for (int i = 0; i < maxRows; i++) {
            OrderUpNetworking.MemberData member = members.get(i);
            int rowY = top + 128 + i * 22;

            renderPlayerFace(graphics, member.id(), member.name(), left + 28, rowY, 18);
            graphics.drawString(font, member.name(), left + 53, rowY + 5, 0xFF3B2A1D, false);

            if (isLocalPlayerOwner() && !member.id().equals(data.ownerId())) {
                graphics.drawString(font, "✕", left + 255, rowY + 5, 0xFF9D2F2F, false);
            }
        }

        if (addMode) {
            int headX = left + 42;
            int headY = top + 166;
            graphics.fill(headX, headY, headX + 18, headY + 18, 0xFF1A1A1A);
            graphics.drawCenteredString(font, "?", headX + 9, headY + 5, 0xFFFFFFFF);
        } else if (members.size() > 3) {
            graphics.drawString(
                    font,
                    "+" + (members.size() - 3) + " more",
                    left + 197,
                    top + 177,
                    0xFF6A5646,
                    false
            );
        }
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
            List<OrderUpNetworking.MemberData> members = data.members();

            for (int i = 0; i < Math.min(3, members.size()); i++) {
                OrderUpNetworking.MemberData member = members.get(i);
                int rowY = top + 128 + i * 22;

                if (!member.id().equals(data.ownerId())
                        && mouseX >= left + 246 && mouseX <= left + 278
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
