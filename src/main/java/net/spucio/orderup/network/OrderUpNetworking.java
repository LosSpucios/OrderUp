package net.spucio.orderup.network;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.client.ClientPayloadHandler;
import net.spucio.orderup.restaurant.RestaurantManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class OrderUpNetworking {
    private OrderUpNetworking() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("3");

        registrar.playToServer(AddMemberPayload.TYPE, AddMemberPayload.STREAM_CODEC, OrderUpNetworking::handleAddMember);
        registrar.playToServer(RemoveMemberPayload.TYPE, RemoveMemberPayload.STREAM_CODEC, OrderUpNetworking::handleRemoveMember);
        registrar.playToServer(RenameRestaurantPayload.TYPE, RenameRestaurantPayload.STREAM_CODEC, OrderUpNetworking::handleRenameRestaurant);
        registrar.playToServer(SetMenuSlotPayload.TYPE, SetMenuSlotPayload.STREAM_CODEC, OrderUpNetworking::handleSetMenuSlot);

        registrar.playToClient(HeartDataPayload.TYPE, HeartDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleHeartData(payload)));
        registrar.playToClient(MenuDataPayload.TYPE, MenuDataPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleMenuData(payload)));
        registrar.playToClient(HudPayload.TYPE, HudPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleHud(payload)));
        registrar.playToClient(BorderTogglePayload.TYPE, BorderTogglePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPayloadHandler.handleBorderToggle(payload)));
    }

    public static void sendHeartData(ServerPlayer player, RestaurantHeartBlockEntity heart) {
        List<MemberData> members = heart.getMembers().entrySet().stream()
                .map(entry -> new MemberData(entry.getKey(), entry.getValue()))
                .toList();
        PacketDistributor.sendToPlayer(player, new HeartDataPayload(
                heart.getBlockPos(),
                heart.getRestaurantName(),
                heart.getRestaurantLevel(),
                heart.getRestaurantXp(),
                heart.xpForNextLevel(),
                heart.getMoney(),
                heart.getOwnerId() == null ? new UUID(0L, 0L) : heart.getOwnerId(),
                heart.getOwnerName(),
                members
        ));
    }

    public static void sendMenuData(ServerPlayer player, MenuBoardBlockEntity menu) {
        List<String> itemIds = new ArrayList<>();
        List<Integer> prices = new ArrayList<>();
        ServerLevel level = player.serverLevel();
        for (int slot = 0; slot < MenuBoardBlockEntity.SLOT_COUNT; slot++) {
            ItemStack stack = menu.getGhostItem(slot);
            itemIds.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            prices.add(stack.isEmpty() ? 0 : menu.getPrice(slot, level));
        }
        PacketDistributor.sendToPlayer(player, new MenuDataPayload(menu.getBlockPos(), itemIds, prices));
    }

    public static void sendHud(
            ServerPlayer player,
            RestaurantHeartBlockEntity heart,
            RestaurantManager.ChairStats chairStats,
            boolean menuComplete,
            boolean openSignPresent,
            boolean restaurantOpen
    ) {
        PacketDistributor.sendToPlayer(player, new HudPayload(
                heart.getBlockPos(),
                heart.getMoney(),
                heart.getRestaurantXp(),
                heart.getRestaurantLevel(),
                heart.xpForNextLevel(),
                chairStats.occupied(),
                chairStats.total(),
                menuComplete,
                openSignPresent,
                restaurantOpen
        ));
    }

    public static void toggleBorder(ServerPlayer player, RestaurantHeartBlockEntity heart) {
        PacketDistributor.sendToPlayer(player, new BorderTogglePayload(heart.getBlockPos(), heart.getRadius()));
    }

    private static void handleAddMember(AddMemberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RestaurantHeartBlockEntity heart = RestaurantManager.get(player.serverLevel(), payload.heartPos()).orElse(null);
            if (heart == null || !heart.isOwner(player.getUUID())) return;
            String name = payload.playerName().strip();
            if (name.isBlank()) return;

            MinecraftServer server = player.getServer();
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                heart.addMember(online.getUUID(), online.getGameProfile().getName());
                sendHeartData(player, heart);
                return;
            }
            Optional<GameProfile> profile = server.getProfileCache().get(name);
            profile.ifPresent(gameProfile -> {
                heart.addMember(gameProfile.getId(), gameProfile.getName());
                sendHeartData(player, heart);
            });
        });
    }

    private static void handleRemoveMember(RemoveMemberPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RestaurantHeartBlockEntity heart = RestaurantManager.get(player.serverLevel(), payload.heartPos()).orElse(null);
            if (heart == null || !heart.isOwner(player.getUUID())) return;
            heart.removeMember(payload.memberId());
            sendHeartData(player, heart);
        });
    }

    private static void handleRenameRestaurant(RenameRestaurantPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            RestaurantHeartBlockEntity heart = RestaurantManager.get(player.serverLevel(), payload.heartPos()).orElse(null);
            if (heart == null || !heart.isOwner(player.getUUID())) return;
            heart.rename(payload.name());
            sendHeartData(player, heart);
        });
    }

    private static void handleSetMenuSlot(SetMenuSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.level().getBlockEntity(payload.menuPos()) instanceof MenuBoardBlockEntity menu)) return;
            RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(player.level(), payload.menuPos()).orElse(null);
            if (heart == null || !heart.isMember(player.getUUID())) return;

            // Repair old or stale Heart <-> Menu links while editing the menu.
            menu.setRestaurantHeartPos(heart.getBlockPos());
            heart.setMenuBoardPos(menu.getBlockPos());

            ItemStack stack = ItemStack.EMPTY;
            if (!payload.itemId().isBlank()) {
                ResourceLocation id = ResourceLocation.tryParse(payload.itemId());
                Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
                if (item != null && item != net.minecraft.world.item.Items.AIR) stack = new ItemStack(item);
            }
            menu.setGhostItem(payload.slot(), stack);
            sendMenuData(player, menu);
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(OrderUp.MOD_ID, path);
    }

    public record MemberData(UUID id, String name) {}

    public record HeartDataPayload(
            BlockPos heartPos,
            String name,
            int level,
            int xp,
            int nextXp,
            long money,
            UUID ownerId,
            String ownerName,
            List<MemberData> members
    ) implements CustomPacketPayload {
        public static final Type<HeartDataPayload> TYPE = new Type<>(id("heart_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HeartDataPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.heartPos);
                    buf.writeUtf(value.name, 64);
                    buf.writeVarInt(value.level);
                    buf.writeVarInt(value.xp);
                    buf.writeVarInt(value.nextXp);
                    buf.writeLong(value.money);
                    buf.writeUUID(value.ownerId);
                    buf.writeUtf(value.ownerName, 32);
                    buf.writeVarInt(value.members.size());
                    for (MemberData member : value.members) {
                        buf.writeUUID(member.id());
                        buf.writeUtf(member.name(), 32);
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    String name = buf.readUtf(64);
                    int level = buf.readVarInt();
                    int xp = buf.readVarInt();
                    int nextXp = buf.readVarInt();
                    long money = buf.readLong();
                    UUID ownerId = buf.readUUID();
                    String ownerName = buf.readUtf(32);
                    int count = buf.readVarInt();
                    List<MemberData> members = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) members.add(new MemberData(buf.readUUID(), buf.readUtf(32)));
                    return new HeartDataPayload(pos, name, level, xp, nextXp, money, ownerId, ownerName, members);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record MenuDataPayload(BlockPos menuPos, List<String> itemIds, List<Integer> prices) implements CustomPacketPayload {
        public static final Type<MenuDataPayload> TYPE = new Type<>(id("menu_data"));
        public static final StreamCodec<RegistryFriendlyByteBuf, MenuDataPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.menuPos);
                    buf.writeVarInt(value.itemIds.size());
                    for (int i = 0; i < value.itemIds.size(); i++) {
                        buf.writeUtf(value.itemIds.get(i), 128);
                        buf.writeVarInt(value.prices.get(i));
                    }
                },
                buf -> {
                    BlockPos pos = buf.readBlockPos();
                    int count = buf.readVarInt();
                    List<String> ids = new ArrayList<>(count);
                    List<Integer> prices = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        ids.add(buf.readUtf(128));
                        prices.add(buf.readVarInt());
                    }
                    return new MenuDataPayload(pos, ids, prices);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record HudPayload(
            BlockPos heartPos,
            long money,
            int xp,
            int level,
            int nextXp,
            int occupiedChairs,
            int totalChairs,
            boolean menuComplete,
            boolean openSignPresent,
            boolean restaurantOpen
    ) implements CustomPacketPayload {
        public static final Type<HudPayload> TYPE = new Type<>(id("hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, HudPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> {
                    buf.writeBlockPos(value.heartPos);
                    buf.writeLong(value.money);
                    buf.writeVarInt(value.xp);
                    buf.writeVarInt(value.level);
                    buf.writeVarInt(value.nextXp);
                    buf.writeVarInt(value.occupiedChairs);
                    buf.writeVarInt(value.totalChairs);
                    buf.writeBoolean(value.menuComplete);
                    buf.writeBoolean(value.openSignPresent);
                    buf.writeBoolean(value.restaurantOpen);
                },
                buf -> new HudPayload(
                        buf.readBlockPos(),
                        buf.readLong(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readBoolean()
                )
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BorderTogglePayload(BlockPos heartPos, int radius) implements CustomPacketPayload {
        public static final Type<BorderTogglePayload> TYPE = new Type<>(id("border_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BorderTogglePayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBlockPos(value.heartPos); buf.writeVarInt(value.radius); },
                buf -> new BorderTogglePayload(buf.readBlockPos(), buf.readVarInt())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record AddMemberPayload(BlockPos heartPos, String playerName) implements CustomPacketPayload {
        public static final Type<AddMemberPayload> TYPE = new Type<>(id("add_member"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AddMemberPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBlockPos(value.heartPos); buf.writeUtf(value.playerName, 32); },
                buf -> new AddMemberPayload(buf.readBlockPos(), buf.readUtf(32))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RemoveMemberPayload(BlockPos heartPos, UUID memberId) implements CustomPacketPayload {
        public static final Type<RemoveMemberPayload> TYPE = new Type<>(id("remove_member"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoveMemberPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBlockPos(value.heartPos); buf.writeUUID(value.memberId); },
                buf -> new RemoveMemberPayload(buf.readBlockPos(), buf.readUUID())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record RenameRestaurantPayload(BlockPos heartPos, String name) implements CustomPacketPayload {
        public static final Type<RenameRestaurantPayload> TYPE = new Type<>(id("rename_restaurant"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RenameRestaurantPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBlockPos(value.heartPos); buf.writeUtf(value.name, 64); },
                buf -> new RenameRestaurantPayload(buf.readBlockPos(), buf.readUtf(64))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetMenuSlotPayload(BlockPos menuPos, int slot, String itemId) implements CustomPacketPayload {
        public static final Type<SetMenuSlotPayload> TYPE = new Type<>(id("set_menu_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetMenuSlotPayload> STREAM_CODEC = StreamCodec.of(
                (buf, value) -> { buf.writeBlockPos(value.menuPos); buf.writeVarInt(value.slot); buf.writeUtf(value.itemId, 128); },
                buf -> new SetMenuSlotPayload(buf.readBlockPos(), buf.readVarInt(), buf.readUtf(128))
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
