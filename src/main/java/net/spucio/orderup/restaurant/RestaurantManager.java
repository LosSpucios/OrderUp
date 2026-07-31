package net.spucio.orderup.restaurant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.entity.CustomerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class RestaurantManager {
    private static final Map<ServerLevel, Map<BlockPos, RestaurantHeartBlockEntity>> HEARTS = new IdentityHashMap<>();

    private RestaurantManager() {}

    public static synchronized void register(RestaurantHeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        HEARTS.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>())
                .put(heart.getBlockPos().immutable(), heart);
    }

    public static synchronized void unregister(RestaurantHeartBlockEntity heart) {
        if (!(heart.getLevel() instanceof ServerLevel level)) return;
        Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(level);
        if (map == null) return;
        map.remove(heart.getBlockPos());
        if (map.isEmpty()) HEARTS.remove(level);
    }

    public static Optional<RestaurantHeartBlockEntity> get(ServerLevel level, BlockPos pos) {
        synchronized (RestaurantManager.class) {
            Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(level);
            if (map == null) return Optional.empty();
            RestaurantHeartBlockEntity heart = map.get(pos);
            return heart != null && !heart.isRemoved() ? Optional.of(heart) : Optional.empty();
        }
    }

    public static Optional<RestaurantHeartBlockEntity> findContaining(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return Optional.empty();
        synchronized (RestaurantManager.class) {
            Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(serverLevel);
            if (map == null) return Optional.empty();
            return map.values().stream()
                    .filter(heart -> !heart.isRemoved() && heart.contains(pos))
                    .min(Comparator.comparingDouble(heart -> heart.getBlockPos().distSqr(pos)));
        }
    }

    public static boolean isChunkClaimedByOther(
            ServerLevel level,
            RestaurantHeartBlockEntity ignoredHeart,
            int chunkX,
            int chunkZ
    ) {
        synchronized (RestaurantManager.class) {
            Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(level);
            if (map == null) return false;
            for (RestaurantHeartBlockEntity heart : map.values()) {
                if (heart == ignoredHeart || heart.isRemoved()) continue;
                if (heart.claimsChunk(chunkX, chunkZ)) return true;
            }
            return false;
        }
    }

    public static List<Long> getChunksClaimedByOtherRestaurants(
            ServerLevel level,
            RestaurantHeartBlockEntity ignoredHeart
    ) {
        synchronized (RestaurantManager.class) {
            Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(level);
            if (map == null) return List.of();

            java.util.LinkedHashSet<Long> claimed = new java.util.LinkedHashSet<>();
            for (RestaurantHeartBlockEntity heart : map.values()) {
                if (heart == ignoredHeart || heart.isRemoved()) continue;
                claimed.addAll(heart.getClaimedChunkKeys());
            }
            return List.copyOf(claimed);
        }
    }

    public static synchronized void clear(ServerLevel level) {
        HEARTS.remove(level);
    }

    public static List<RestaurantHeartBlockEntity> all(ServerLevel level) {
        synchronized (RestaurantManager.class) {
            Map<BlockPos, RestaurantHeartBlockEntity> map = HEARTS.get(level);
            if (map == null) return List.of();
            return List.copyOf(map.values());
        }
    }

    public static List<BlockPos> findFreeChairs(ServerLevel level, RestaurantHeartBlockEntity heart) {
        Set<BlockPos> reservedChairs = reservedChairPositions(level, heart);
        return findChairPositions(level, heart).stream()
                .filter(chair -> !reservedChairs.contains(chair))
                .toList();
    }

    public static ChairStats getChairStats(ServerLevel level, RestaurantHeartBlockEntity heart) {
        Set<BlockPos> reservedChairs = reservedChairPositions(level, heart);
        List<BlockPos> chairs = findChairPositions(level, heart);
        int occupied = 0;
        for (BlockPos chair : chairs) {
            if (reservedChairs.contains(chair)) occupied++;
        }
        return new ChairStats(occupied, chairs.size());
    }

    private static List<BlockPos> findChairPositions(
            ServerLevel level,
            RestaurantHeartBlockEntity heart
    ) {
        List<BlockPos> chairs = new ArrayList<>();
        for (ChunkPos chunkPos : heart.getClaimedChunks()) {
            if (!level.hasChunk(chunkPos.x, chunkPos.z)) continue;
            LevelChunkSection[] sections = level.getChunk(chunkPos.x, chunkPos.z).getSections();
            for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                LevelChunkSection section = sections[sectionIndex];
                if (section == null || section.hasOnlyAir()
                        || !section.maybeHas(state -> state.is(ModContent.CHAIR.get()))) {
                    continue;
                }

                int sectionY = level.getSectionYFromSectionIndex(sectionIndex);
                int baseY = SectionPos.sectionToBlockCoord(sectionY);
                for (int localY = 0; localY < 16; localY++) {
                    for (int localX = 0; localX < 16; localX++) {
                        for (int localZ = 0; localZ < 16; localZ++) {
                            if (!section.getBlockState(localX, localY, localZ).is(ModContent.CHAIR.get())) continue;
                            chairs.add(new BlockPos(
                                    chunkPos.getBlockX(localX),
                                    baseY + localY,
                                    chunkPos.getBlockZ(localZ)
                            ));
                        }
                    }
                }
            }
        }
        return chairs;
    }

    private static Set<BlockPos> reservedChairPositions(
            ServerLevel level,
            RestaurantHeartBlockEntity heart
    ) {
        AABB claimedBounds = claimedBounds(level, heart, 40.0D);
        Set<BlockPos> reserved = new HashSet<>();
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                claimedBounds,
                entity -> entity.belongsTo(heart.getBlockPos()) && !entity.isLeaving()
        )) {
            BlockPos chair = customer.getTargetChair();
            if (chair != null) reserved.add(chair.immutable());
        }
        return reserved;
    }

    private static AABB claimedBounds(ServerLevel level, RestaurantHeartBlockEntity heart, double inflate) {
        List<ChunkPos> chunks = heart.getClaimedChunks();
        int minX = heart.getBlockPos().getX();
        int maxX = minX;
        int minZ = heart.getBlockPos().getZ();
        int maxZ = minZ;
        for (ChunkPos chunk : chunks) {
            minX = Math.min(minX, chunk.getMinBlockX());
            maxX = Math.max(maxX, chunk.getMaxBlockX() + 1);
            minZ = Math.min(minZ, chunk.getMinBlockZ());
            maxZ = Math.max(maxZ, chunk.getMaxBlockZ() + 1);
        }
        return new AABB(
                minX - inflate,
                level.getMinBuildHeight(),
                minZ - inflate,
                maxX + inflate,
                level.getMaxBuildHeight(),
                maxZ + inflate
        );
    }

    public static boolean isChairFree(ServerLevel level, RestaurantHeartBlockEntity heart, BlockPos chairPos) {
        if (!heart.contains(chairPos) || !level.getBlockState(chairPos).is(ModContent.CHAIR.get())) return false;
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                new AABB(chairPos).inflate(2.0D),
                entity -> entity.belongsTo(heart.getBlockPos())
        )) {
            if (chairPos.equals(customer.getTargetChair()) && !customer.isLeaving()) return false;
        }
        return true;
    }

    public static List<MenuBoardBlockEntity> findMenuBoards(
            ServerLevel level,
            RestaurantHeartBlockEntity heart
    ) {
        List<MenuBoardBlockEntity> menus = new ArrayList<>();
        for (ChunkPos chunk : heart.getClaimedChunks()) {
            if (!level.hasChunk(chunk.x, chunk.z)) continue;
            for (var blockEntity : level.getChunk(chunk.x, chunk.z).getBlockEntities().values()) {
                if (blockEntity instanceof MenuBoardBlockEntity menu) {
                    menus.add(menu);
                }
            }
        }
        return menus;
    }

    /**
     * Makes every Menu Board inside one restaurant use the same six ghost slots.
     */
    public static void synchronizeMenuBoards(
            ServerLevel level,
            RestaurantHeartBlockEntity heart,
            MenuBoardBlockEntity preferredMenu
    ) {
        synchronizeMenuBoardsInternal(level, heart, preferredMenu);
        heart.syncHudNow(level);
    }

    public static boolean setSharedMenuItem(
            ServerLevel level,
            RestaurantHeartBlockEntity heart,
            MenuBoardBlockEntity editedMenu,
            int slot,
            ItemStack stack
    ) {
        List<MenuBoardBlockEntity> menus = synchronizeMenuBoardsInternal(level, heart, editedMenu);
        if (menus.isEmpty()) return false;

        MenuBoardBlockEntity source = menus.contains(editedMenu) ? editedMenu : menus.getFirst();
        List<ItemStack> sharedItems = new ArrayList<>(source.getGhostItems());
        if (!MenuBoardBlockEntity.isValidMenuChange(sharedItems, slot, stack)) return false;

        sharedItems.set(slot, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        BlockPos heartPos = heart.getBlockPos();
        for (MenuBoardBlockEntity menu : menus) {
            menu.applySharedMenu(sharedItems, heartPos);
        }

        heart.setMenuBoardPos(source.getBlockPos());
        heart.syncHudNow(level);
        return true;
    }

    private static List<MenuBoardBlockEntity> synchronizeMenuBoardsInternal(
            ServerLevel level,
            RestaurantHeartBlockEntity heart,
            MenuBoardBlockEntity preferredMenu
    ) {
        List<MenuBoardBlockEntity> menus = new ArrayList<>(findMenuBoards(level, heart));
        if (preferredMenu != null && heart.contains(preferredMenu.getBlockPos()) && !menus.contains(preferredMenu)) {
            menus.add(preferredMenu);
        }

        if (menus.isEmpty()) {
            heart.setMenuBoardPos(null);
            return menus;
        }

        BlockPos linkedPos = heart.getMenuBoardPos();
        MenuBoardBlockEntity source = null;
        int bestFilled = -1;
        int bestPriority = -1;
        for (MenuBoardBlockEntity menu : menus) {
            int filled = menu.getFilledSlotCount();
            int priority = menu.getBlockPos().equals(linkedPos) ? 2 : menu == preferredMenu ? 1 : 0;
            if (filled > bestFilled || filled == bestFilled && priority > bestPriority) {
                source = menu;
                bestFilled = filled;
                bestPriority = priority;
            }
        }

        if (source == null) return menus;
        List<ItemStack> sharedItems = source.getGhostItems();
        BlockPos heartPos = heart.getBlockPos();
        for (MenuBoardBlockEntity menu : menus) {
            menu.applySharedMenu(sharedItems, heartPos);
        }
        heart.setMenuBoardPos(source.getBlockPos());
        return menus;
    }

    public static void removeCustomersForHeart(ServerLevel level, BlockPos heartPos) {
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                new AABB(
                        heartPos.getX() - 256.0D, level.getMinBuildHeight(), heartPos.getZ() - 256.0D,
                        heartPos.getX() + 257.0D, level.getMaxBuildHeight(), heartPos.getZ() + 257.0D
                ),
                entity -> entity.belongsTo(heartPos)
        )) {
            customer.onRestaurantRemoved();
        }
    }

    public static boolean isInside(Entity entity, RestaurantHeartBlockEntity heart) {
        return heart.contains(entity.blockPosition());
    }

    public record ChairStats(int occupied, int total) {}
}
