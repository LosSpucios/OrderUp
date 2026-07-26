package net.spucio.orderup.blockentity;

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
import org.jetbrains.annotations.Nullable;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.block.OpenSignBlock;
import net.spucio.orderup.entity.CustomerEntity;
import net.spucio.orderup.network.OrderUpNetworking;
import net.spucio.orderup.restaurant.RestaurantManager;

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
    private boolean open;
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
        return containsPosition(pos.getX() + 0.5D, pos.getZ() + 0.5D);
    }

    public boolean containsPosition(double x, double z) {
        int radius = getRadius();
        double centerX = worldPosition.getX() + 0.5D;
        double centerZ = worldPosition.getZ() + 0.5D;
        return x >= centerX - radius
                && x <= centerX + radius
                && z >= centerZ - radius
                && z <= centerZ + radius;
    }

    public boolean contains(Entity entity) {
        return containsPosition(entity.getX(), entity.getZ());
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
        if (level instanceof ServerLevel serverLevel) syncHudNow(serverLevel);
    }

    public void addMoney(long amount) {
        money = Math.max(0, money + amount);
        setChanged();
        if (level instanceof ServerLevel serverLevel) syncHudNow(serverLevel);
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

    public void syncHudNow(ServerLevel level) {
        validateLinkedBlocks(level);
        syncHudToNearbyMembers(level);
    }

    private void syncHudToNearbyMembers(ServerLevel level) {
        RestaurantManager.ChairStats chairStats = RestaurantManager.getChairStats(level, this);
        boolean menuComplete = isMenuComplete(level);
        SignStatus signStatus = resolveSignStatus(level);

        for (ServerPlayer player : level.players()) {
            if (isMember(player.getUUID()) && contains(player.blockPosition())) {
                OrderUpNetworking.sendHud(player, this, chairStats, menuComplete, signStatus.present(), signStatus.open());
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
        SignStatus signStatus = resolveSignStatus(level);
        boolean menuComplete = isMenuComplete(level);
        List<BlockPos> freeChairs = RestaurantManager.findFreeChairs(level, this);

        if (!signStatus.open() || !menuComplete || freeChairs.isEmpty()) {
            nextCustomerSpawnTick = 0L;
            return;
        }

        long gameTime = level.getGameTime();
        if (nextCustomerSpawnTick <= 0L) {
            scheduleNextCustomer(level);
            return;
        }
        if (gameTime < nextCustomerSpawnTick) return;

        if (trySpawnCustomer(level, freeChairs)) {
            if (RestaurantManager.findFreeChairs(level, this).isEmpty()) {
                nextCustomerSpawnTick = 0L;
            } else {
                scheduleNextCustomer(level);
            }
        } else {
            // A blocked edge must not permanently freeze the restaurant.
            nextCustomerSpawnTick = gameTime + 20L;
        }
    }

    private void scheduleNextCustomer(ServerLevel level) {
        // Five to ten seconds after all requirements become valid.
        nextCustomerSpawnTick = level.getGameTime() + 100L + level.random.nextInt(101);
    }

    public SignStatus resolveSignStatus(ServerLevel level) {
        BlockPos signPos = openSignPos;
        if (signPos == null || !level.getBlockState(signPos).is(ModContent.OPEN_SIGN.get())) {
            signPos = findOpenSignInRestaurant(level);
            if (signPos != null) {
                setOpenSignPos(signPos);
            }
        }

        if (signPos == null) {
            updateOpenState(false);
            return new SignStatus(false, false);
        }

        BlockState signState = level.getBlockState(signPos);
        if (!signState.is(ModContent.OPEN_SIGN.get()) || !signState.hasProperty(OpenSignBlock.OPEN)) {
            setOpenSignPos(null);
            updateOpenState(false);
            return new SignStatus(false, false);
        }

        boolean signOpen = signState.getValue(OpenSignBlock.OPEN);
        updateOpenState(signOpen);
        return new SignStatus(true, signOpen);
    }

    private void updateOpenState(boolean value) {
        if (open == value) return;
        open = value;
        nextCustomerSpawnTick = 0L;
        setChanged();
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

        List<BlockPos> chairs = new ArrayList<>(freeChairs);
        shuffle(level, chairs);
        List<BlockPos> spawnCandidates = collectSpawnCandidates(level);
        if (spawnCandidates.isEmpty()) return false;

        for (BlockPos chair : chairs) {
            for (BlockPos spawnPos : spawnCandidates) {
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
                customer.setPersistenceRequired();

                if (!level.noCollision(customer)) continue;
                if (!level.addFreshEntity(customer)) continue;

                // Navigation can require the entity to exist in the world for one tick.
                // The customer stays spawned and retries by itself instead of being discarded.
                customer.beginWalkingToChair();
                syncHudNow(level);
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> collectSpawnCandidates(ServerLevel level) {
        List<BlockPos> columns = new ArrayList<>();
        int radius = getRadius();

        // Every valid perimeter column is considered, not just a few random guesses.
        // The resulting list is shuffled, so customers still arrive from random directions.
        for (int outside = 4; outside <= 5; outside++) {
            int distance = radius + outside;
            for (int offset = -radius; offset <= radius; offset++) {
                columns.add(new BlockPos(worldPosition.getX() + distance, worldPosition.getY(), worldPosition.getZ() + offset));
                columns.add(new BlockPos(worldPosition.getX() - distance, worldPosition.getY(), worldPosition.getZ() + offset));
                columns.add(new BlockPos(worldPosition.getX() + offset, worldPosition.getY(), worldPosition.getZ() + distance));
                columns.add(new BlockPos(worldPosition.getX() + offset, worldPosition.getY(), worldPosition.getZ() - distance));
            }
        }
        shuffle(level, columns);

        List<BlockPos> safePositions = new ArrayList<>();
        for (BlockPos column : columns) {
            BlockPos safe = findSafeSpawnInColumn(level, column.getX(), column.getZ());
            if (safe != null) safePositions.add(safe);
        }
        return safePositions;
    }

    @Nullable
    private static BlockPos findSafeSpawnInColumn(ServerLevel level, int x, int z) {
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        // Heightmap normally points at the first air block. The extra range also handles
        // slabs, paths, small slopes and structures around the restaurant border.
        for (int delta = 3; delta >= -10; delta--) {
            BlockPos candidate = new BlockPos(x, surfaceY + delta, z);
            if (isSafeCustomerSpawn(level, candidate)) return candidate;
        }
        return null;
    }

    private static boolean isSafeCustomerSpawn(ServerLevel level, BlockPos pos) {
        BlockPos floorPos = pos.below();
        return level.isInWorldBounds(pos)
                && level.isInWorldBounds(pos.above())
                && level.getWorldBorder().isWithinBounds(pos)
                && level.getFluidState(pos).isEmpty()
                && level.getFluidState(pos.above()).isEmpty()
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && level.getBlockState(floorPos).isFaceSturdy(level, floorPos, Direction.UP);
    }

    private static <T> void shuffle(ServerLevel level, List<T> values) {
        for (int i = values.size() - 1; i > 0; i--) {
            int other = level.random.nextInt(i + 1);
            Collections.swap(values, i, other);
        }
    }

    public record SignStatus(boolean present, boolean open) {}

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
        open = tag.contains("Open") && tag.getBoolean("Open");
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
