package pl.losspucios.orderup.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.losspucios.orderup.ModContent;
import pl.losspucios.orderup.block.OpenSignBlock;
import pl.losspucios.orderup.entity.CustomerEntity;
import pl.losspucios.orderup.network.OrderUpNetworking;
import pl.losspucios.orderup.restaurant.RestaurantManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RestaurantHeartBlockEntity extends BlockEntity {
    private String restaurantName = "My Restaurant";
    private UUID ownerId;
    private String ownerName = "Unknown";
    private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
    private int restaurantLevel = 1;
    private int restaurantXp;
    private long money;
    private boolean open = true;
    private BlockPos menuBoardPos;
    private BlockPos openSignPos;
    private long nextCustomerSpawnTick;

    public RestaurantHeartBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.RESTAURANT_HEART_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        RestaurantManager.register(this);
    }

    @Override
    public void setRemoved() {
        RestaurantManager.unregister(this);
        super.setRemoved();
    }

    public static void serverTick(ServerLevel level, BlockPos pos, BlockState state, RestaurantHeartBlockEntity heart) {
        long gameTime = level.getGameTime();
        if (gameTime % 20L == 0L) {
            heart.syncHudToNearbyMembers(level);
            heart.validateLinkedBlocks(level);
        }
        if (gameTime % 10L == 0L) {
            heart.tickCustomerSpawner(level);
        }
    }

    public void initializeOwner(ServerPlayer player) {
        if (ownerId != null) return;
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        members.put(ownerId, ownerName);
        restaurantName = ownerName + "'s Restaurant";
        setChanged();
    }

    public boolean contains(BlockPos pos) {
        int radius = getRadius();
        return Math.abs(pos.getX() - worldPosition.getX()) <= radius
                && Math.abs(pos.getZ() - worldPosition.getZ()) <= radius;
    }

    public boolean contains(Entity entity) {
        return contains(entity.blockPosition());
    }

    public int getRadius() {
        // Level 1 starts with an 8-block horizontal radius; every level adds 4 blocks.
        return 4 + restaurantLevel * 4;
    }

    public int xpForNextLevel() {
        return 100 + (restaurantLevel - 1) * 150;
    }

    public void addRestaurantXp(int amount) {
        if (amount <= 0) return;
        restaurantXp += amount;
        while (restaurantXp >= xpForNextLevel()) {
            restaurantXp -= xpForNextLevel();
            restaurantLevel++;
        }
        setChanged();
    }

    public void addMoney(long amount) {
        money = Math.max(0, money + amount);
        setChanged();
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public boolean isOwner(UUID uuid) {
        return ownerId != null && ownerId.equals(uuid);
    }

    public boolean addMember(UUID uuid, String name) {
        if (uuid == null || name == null || name.isBlank()) return false;
        members.put(uuid, name);
        setChanged();
        return true;
    }

    public boolean removeMember(UUID uuid) {
        if (uuid == null || uuid.equals(ownerId)) return false;
        boolean removed = members.remove(uuid) != null;
        if (removed) setChanged();
        return removed;
    }

    public void rename(String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isBlank()) clean = "My Restaurant";
        if (clean.length() > 32) clean = clean.substring(0, 32);
        restaurantName = clean;
        setChanged();
    }

    public void setMenuBoardPos(BlockPos pos) {
        BlockPos newPos = pos == null ? null : pos.immutable();
        if (java.util.Objects.equals(menuBoardPos, newPos)) {
            return;
        }
        menuBoardPos = newPos;
        nextCustomerSpawnTick = 0L;
        setChanged();
    }

    public void setOpenSignPos(BlockPos pos) {
        BlockPos newPos = pos == null ? null : pos.immutable();
        if (java.util.Objects.equals(openSignPos, newPos)) {
            return;
        }
        openSignPos = newPos;
        nextCustomerSpawnTick = 0L;
        setChanged();
    }

    public void setOpen(boolean value) {
        open = value;
        nextCustomerSpawnTick = 0L;
        setChanged();
    }

    public void sendSnapshot(ServerPlayer player) {
        OrderUpNetworking.sendHeartData(player, this);
    }

    private void syncHudToNearbyMembers(ServerLevel level) {
        RestaurantManager.ChairStats chairStats = RestaurantManager.getChairStats(level, this);
        boolean menuComplete = isMenuComplete(level);

        for (ServerPlayer player : level.players()) {
            if (isMember(player.getUUID()) && contains(player.blockPosition())) {
                OrderUpNetworking.sendHud(player, this, chairStats, menuComplete);
            }
        }
    }

    public boolean isMenuComplete(ServerLevel level) {
        MenuBoardBlockEntity linkedMenu = getLinkedMenu(level);
        if (linkedMenu != null && linkedMenu.isFull()) {
            ensureMenuLink(linkedMenu);
            return true;
        }

        // Existing worlds can have a filled menu whose link to the Heart was lost.
        // Search the restaurant area and repair the link automatically.
        MenuBoardBlockEntity completeMenu = findMenuInRestaurant(level, true);
        if (completeMenu != null) {
            ensureMenuLink(completeMenu);
            return true;
        }

        // Keep at least one incomplete menu linked, so the HUD changes to a checkmark
        // immediately after its final slot is filled.
        if (linkedMenu == null) {
            MenuBoardBlockEntity anyMenu = findMenuInRestaurant(level, false);
            if (anyMenu != null) {
                ensureMenuLink(anyMenu);
            }
        }
        return false;
    }

    private MenuBoardBlockEntity getLinkedMenu(ServerLevel level) {
        if (menuBoardPos == null) {
            return null;
        }
        if (!level.hasChunk(menuBoardPos.getX() >> 4, menuBoardPos.getZ() >> 4)) {
            return null;
        }
        if (!level.getBlockState(menuBoardPos).is(ModContent.MENU_BOARD.get())) {
            return null;
        }
        return level.getBlockEntity(menuBoardPos) instanceof MenuBoardBlockEntity menu ? menu : null;
    }

    private MenuBoardBlockEntity findMenuInRestaurant(ServerLevel level, boolean requireFull) {
        int radius = getRadius();
        int minY = Math.max(level.getMinBuildHeight(), worldPosition.getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, worldPosition.getY() + 8);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = worldPosition.getX() - radius; x <= worldPosition.getX() + radius; x++) {
            for (int z = worldPosition.getZ() - radius; z <= worldPosition.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.getBlockState(cursor).is(ModContent.MENU_BOARD.get())) {
                        continue;
                    }
                    if (level.getBlockEntity(cursor) instanceof MenuBoardBlockEntity menu
                            && (!requireFull || menu.isFull())) {
                        return menu;
                    }
                }
            }
        }
        return null;
    }

    private void ensureMenuLink(MenuBoardBlockEntity menu) {
        BlockPos position = menu.getBlockPos();
        menu.setRestaurantHeartPos(worldPosition);
        setMenuBoardPos(position);
    }

    private void validateLinkedBlocks(ServerLevel level) {
        if (menuBoardPos != null && !level.getBlockState(menuBoardPos).is(ModContent.MENU_BOARD.get())) {
            setMenuBoardPos(null);
        }
        if (openSignPos != null && !level.getBlockState(openSignPos).is(ModContent.OPEN_SIGN.get())) {
            setOpenSignPos(null);
        }
    }

    private void tickCustomerSpawner(ServerLevel level) {
        List<BlockPos> freeChairs = RestaurantManager.findFreeChairs(level, this);
        if (!hasOpenSignEnabled(level) || !isMenuComplete(level) || freeChairs.isEmpty()) {
            nextCustomerSpawnTick = 0L;
            return;
        }

        long gameTime = level.getGameTime();
        if (nextCustomerSpawnTick <= 0L) {
            scheduleNextCustomer(level);
            return;
        }
        if (gameTime < nextCustomerSpawnTick) return;

        boolean spawned = trySpawnCustomer(level, freeChairs);
        if (!spawned) {
            // Conditions are valid, so retry quickly if terrain blocked the chosen edge positions.
            nextCustomerSpawnTick = gameTime + 20L;
            return;
        }

        if (RestaurantManager.findFreeChairs(level, this).isEmpty()) {
            nextCustomerSpawnTick = 0L;
        } else {
            scheduleNextCustomer(level);
        }
    }

    private void scheduleNextCustomer(ServerLevel level) {
        // Five to ten seconds after all requirements become valid.
        nextCustomerSpawnTick = level.getGameTime() + 100L + level.random.nextInt(101);
    }

    private boolean hasOpenSignEnabled(ServerLevel level) {
        BlockPos signPos = openSignPos;
        if (signPos == null || !level.getBlockState(signPos).is(ModContent.OPEN_SIGN.get())) {
            signPos = findOpenSignInRestaurant(level);
            if (signPos == null) return false;
            setOpenSignPos(signPos);
        }

        if (!level.hasChunk(signPos.getX() >> 4, signPos.getZ() >> 4)) return false;
        BlockState signState = level.getBlockState(signPos);
        if (!signState.is(ModContent.OPEN_SIGN.get()) || !signState.hasProperty(OpenSignBlock.OPEN)) return false;

        boolean signOpen = signState.getValue(OpenSignBlock.OPEN);
        if (open != signOpen) {
            open = signOpen;
            setChanged();
        }
        return signOpen;
    }

    private BlockPos findOpenSignInRestaurant(ServerLevel level) {
        int radius = getRadius();
        int minY = Math.max(level.getMinBuildHeight(), worldPosition.getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, worldPosition.getY() + 8);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = worldPosition.getX() - radius; x <= worldPosition.getX() + radius; x++) {
            for (int z = worldPosition.getZ() - radius; z <= worldPosition.getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).is(ModContent.OPEN_SIGN.get())) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private boolean trySpawnCustomer(ServerLevel level, List<BlockPos> freeChairs) {
        if (menuBoardPos == null || freeChairs.isEmpty()) return false;

        BlockPos chair = freeChairs.get(level.random.nextInt(freeChairs.size()));
        for (int attempt = 0; attempt < 24; attempt++) {
            BlockPos spawnPos = randomSpawnPosition(level);
            if (spawnPos == null) continue;

            CustomerEntity customer = ModContent.CUSTOMER.get().create(level);
            if (customer == null) return false;
            customer.moveTo(
                    spawnPos.getX() + 0.5D,
                    spawnPos.getY(),
                    spawnPos.getZ() + 0.5D,
                    level.random.nextFloat() * 360.0F,
                    0.0F
            );
            customer.setRestaurantContext(worldPosition, chair, menuBoardPos);

            if (!level.noCollision(customer)) continue;

            // Navigation is reliable only after the entity has joined the level.
            if (!level.addFreshEntity(customer)) continue;
            if (!customer.beginWalkingToChair()) {
                customer.discard();
                continue;
            }
            return true;
        }
        return false;
    }

    private BlockPos randomSpawnPosition(ServerLevel level) {
        // Spawn four or five blocks beyond the visible restaurant border.
        int distance = getRadius() + 4 + level.random.nextInt(2);
        int side = level.random.nextInt(4);
        int offset = level.random.nextInt(getRadius() * 2 + 1) - getRadius();
        int x = worldPosition.getX();
        int z = worldPosition.getZ();
        switch (side) {
            case 0 -> { x += distance; z += offset; }
            case 1 -> { x -= distance; z += offset; }
            case 2 -> { z += distance; x += offset; }
            default -> { z -= distance; x += offset; }
        }

        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos spawnPos = new BlockPos(x, y, z);
        BlockPos floorPos = spawnPos.below();

        if (!level.getFluidState(spawnPos).isEmpty()) return null;
        if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()) return null;
        if (!level.getBlockState(spawnPos.above()).getCollisionShape(level, spawnPos.above()).isEmpty()) return null;
        if (!level.getBlockState(floorPos).isFaceSturdy(level, floorPos, Direction.UP)) return null;
        return spawnPos;
    }

    public String getRestaurantName() { return restaurantName; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName; }
    public Map<UUID, String> getMembers() { return Collections.unmodifiableMap(new LinkedHashMap<>(members)); }
    public int getRestaurantLevel() { return restaurantLevel; }
    public int getRestaurantXp() { return restaurantXp; }
    public long getMoney() { return money; }
    public boolean isOpen() { return open; }
    public BlockPos getMenuBoardPos() { return menuBoardPos; }
    public BlockPos getOpenSignPos() { return openSignPos; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("RestaurantName", restaurantName);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putInt("RestaurantLevel", restaurantLevel);
        tag.putInt("RestaurantXp", restaurantXp);
        tag.putLong("Money", money);
        tag.putBoolean("Open", open);
        if (menuBoardPos != null) tag.putLong("MenuBoardPos", menuBoardPos.asLong());
        if (openSignPos != null) tag.putLong("OpenSignPos", openSignPos.asLong());

        ListTag memberList = new ListTag();
        for (Map.Entry<UUID, String> entry : members.entrySet()) {
            CompoundTag member = new CompoundTag();
            member.putUUID("Id", entry.getKey());
            member.putString("Name", entry.getValue());
            memberList.add(member);
        }
        tag.put("Members", memberList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        restaurantName = tag.getString("RestaurantName");
        if (restaurantName.isBlank()) restaurantName = "My Restaurant";
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        restaurantLevel = Math.max(1, tag.getInt("RestaurantLevel"));
        restaurantXp = Math.max(0, tag.getInt("RestaurantXp"));
        money = Math.max(0L, tag.getLong("Money"));
        open = !tag.contains("Open") || tag.getBoolean("Open");
        nextCustomerSpawnTick = 0L;
        menuBoardPos = tag.contains("MenuBoardPos") ? BlockPos.of(tag.getLong("MenuBoardPos")) : null;
        openSignPos = tag.contains("OpenSignPos") ? BlockPos.of(tag.getLong("OpenSignPos")) : null;

        members.clear();
        ListTag memberList = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < memberList.size(); i++) {
            CompoundTag member = memberList.getCompound(i);
            if (member.hasUUID("Id")) members.put(member.getUUID("Id"), member.getString("Name"));
        }
        if (ownerId != null) members.putIfAbsent(ownerId, ownerName);
    }
}
