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
    }

    @Override
    protected void init() {
        int left = (width - 300) / 2;
        int top = (height - 224) / 2;
        boolean owner = isLocalPlayerOwner();

        restaurantNameBox = new EditBox(font, left + 34, top + 18, 212, 20, Component.translatable("screen.orderup.restaurant_name"));
        restaurantNameBox.setValue(data.name());
        restaurantNameBox.setMaxLength(32);
        restaurantNameBox.setEditable(owner);
        addRenderableWidget(restaurantNameBox);

        saveNameButton = Button.builder(Component.literal("✓"), button -> {
                    PacketDistributor.sendToServer(new OrderUpNetworking.RenameRestaurantPayload(data.heartPos(), restaurantNameBox.getValue()));
                })
                .bounds(left + 252, top + 18, 24, 20)
                .build();
        saveNameButton.visible = owner;
        addRenderableWidget(saveNameButton);

        addButton = Button.builder(Component.literal("+"), button -> setAddMode(true))
                .bounds(left + 34, top + 174, 24, 24)
                .build();
        addButton.visible = owner;
        addRenderableWidget(addButton);

        addMemberBox = new EditBox(font, left + 66, top + 176, 132, 20, Component.translatable("screen.orderup.player_name"));
        addMemberBox.setMaxLength(16);
        addMemberBox.visible = false;
        addRenderableWidget(addMemberBox);

        confirmAddButton = Button.builder(Component.literal("✓"), button -> confirmAddMember())
                .bounds(left + 204, top + 176, 28, 20)
                .build();
        confirmAddButton.visible = false;
        addRenderableWidget(confirmAddButton);

        cancelAddButton = Button.builder(Component.literal("✕"), button -> setAddMode(false))
                .bounds(left + 238, top + 176, 28, 20)
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
        if (value) setInitialFocus(addMemberBox);
        else addMemberBox.setValue("");
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
        if (restaurantNameBox != null && !restaurantNameBox.isFocused()) restaurantNameBox.setValue(payload.name());
    }

    public BlockPos getHeartPos() {
        return data.heartPos();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x55000000);
        int left = (width - 300) / 2;
        int top = (height - 224) / 2;

        // Cozy parchment-and-wood placeholder styling. Final texture can replace this without changing screen logic.
        graphics.fill(left, top, left + 300, top + 224, 0xFFD8B77A);
        graphics.fill(left + 6, top + 6, left + 294, top + 218, 0xFFF5E6C8);
        graphics.fill(left + 26, top + 41, left + 278, top + 43, 0xFF7A4E2D);

        graphics.drawCenteredString(font, Component.translatable("screen.orderup.level", data.level()), left + 150, top + 51, 0xFF4A2D18);
        int barX = left + 50;
        int barY = top + 66;
        int barW = 200;
        graphics.fill(barX, barY, barX + barW, barY + 10, 0xFF6E5A45);
        int fill = (int) (barW * Math.min(1.0D, data.xp() / (double) Math.max(1, data.nextXp())));
        if (fill > 1) {
            graphics.fill(barX + 1, barY + 1, barX + fill - 1, barY + 9, 0xFF7EB45A);
        }
        graphics.drawCenteredString(font, data.xp() + " / " + data.nextXp() + " XP", left + 150, barY + 13, 0xFF5A3A22);

        graphics.drawString(font, Component.translatable("screen.orderup.crew"), left + 28, top + 89, 0xFF4A2D18, false);
        renderMembers(graphics, left, top, mouseX, mouseY);
        if (addMode) {
            int headX = left + 34;
            int headY = top + 176;
            graphics.fill(headX, headY, headX + 20, headY + 20, 0xFF171717);
            graphics.drawCenteredString(font, "?", headX + 10, headY + 6, 0xFFFFFFFF);
        }
        graphics.drawString(font, Component.literal("$" + data.money()), left + 226, top + 91, 0xFF357A38, false);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderMembers(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        List<OrderUpNetworking.MemberData> members = new ArrayList<>(data.members());
        int maxRows = Math.min(3, members.size());
        for (int i = 0; i < maxRows; i++) {
            OrderUpNetworking.MemberData member = members.get(i);
            int rowY = top + 106 + i * 24;
            renderPlayerFace(graphics, member.id(), member.name(), left + 34, rowY, 20);
            graphics.drawString(font, member.name(), left + 62, rowY + 6, 0xFF3B2A1D, false);
            if (isLocalPlayerOwner() && !member.id().equals(data.ownerId())) {
                graphics.drawString(font, "✕", left + 252, rowY + 6, 0xFF9D2F2F, false);
            }
        }
        if (members.size() > 3) {
            graphics.drawString(font, "+" + (members.size() - 3) + " more", left + 184, top + 158, 0xFF6A5646, false);
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
            graphics.drawCenteredString(font, initial, x + size / 2, y + 6, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isLocalPlayerOwner()) {
            int left = (width - 300) / 2;
            int top = (height - 224) / 2;
            List<OrderUpNetworking.MemberData> members = data.members();
            for (int i = 0; i < Math.min(3, members.size()); i++) {
                OrderUpNetworking.MemberData member = members.get(i);
                int rowY = top + 106 + i * 24;
                if (!member.id().equals(data.ownerId())
                        && mouseX >= left + 244 && mouseX <= left + 272
                        && mouseY >= rowY && mouseY <= rowY + 20) {
                    PacketDistributor.sendToServer(new OrderUpNetworking.RemoveMemberPayload(data.heartPos(), member.id()));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isLocalPlayerOwner() {
        return minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(data.ownerId());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
