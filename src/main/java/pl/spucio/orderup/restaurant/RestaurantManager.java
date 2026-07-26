package pl.spucio.orderup.restaurant;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import pl.spucio.orderup.ModContent;
import pl.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import pl.spucio.orderup.entity.CustomerEntity;

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
        int radius = heart.getRadius();
        int minY = Math.max(level.getMinBuildHeight(), heart.getBlockPos().getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, heart.getBlockPos().getY() + 8);
        List<BlockPos> chairs = new ArrayList<>();

        Set<BlockPos> reservedChairs = reservedChairPositions(level, heart);
        for (int x = heart.getBlockPos().getX() - radius; x <= heart.getBlockPos().getX() + radius; x++) {
            for (int z = heart.getBlockPos().getZ() - radius; z <= heart.getBlockPos().getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (level.getBlockState(pos).is(ModContent.CHAIR.get()) && !reservedChairs.contains(pos)) {
                        chairs.add(pos);
                    }
                }
            }
        }
        return chairs;
    }

    public static ChairStats getChairStats(ServerLevel level, RestaurantHeartBlockEntity heart) {
        int radius = heart.getRadius();
        int minY = Math.max(level.getMinBuildHeight(), heart.getBlockPos().getY() - 8);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, heart.getBlockPos().getY() + 8);
        Set<BlockPos> reservedChairs = reservedChairPositions(level, heart);

        int total = 0;
        int occupied = 0;
        for (int x = heart.getBlockPos().getX() - radius; x <= heart.getBlockPos().getX() + radius; x++) {
            for (int z = heart.getBlockPos().getZ() - radius; z <= heart.getBlockPos().getZ() + radius; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).is(ModContent.CHAIR.get())) continue;
                    total++;
                    if (reservedChairs.contains(pos)) occupied++;
                }
            }
        }
        return new ChairStats(occupied, total);
    }

    private static Set<BlockPos> reservedChairPositions(
            ServerLevel level,
            RestaurantHeartBlockEntity heart
    ) {
        int searchRadius = heart.getRadius() + 40;
        AABB searchArea = new AABB(
                heart.getBlockPos().getX() - searchRadius,
                level.getMinBuildHeight(),
                heart.getBlockPos().getZ() - searchRadius,
                heart.getBlockPos().getX() + searchRadius + 1.0D,
                level.getMaxBuildHeight(),
                heart.getBlockPos().getZ() + searchRadius + 1.0D
        );

        Set<BlockPos> reserved = new HashSet<>();
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                searchArea,
                entity -> entity.belongsTo(heart.getBlockPos()) && !entity.isLeaving()
        )) {
            BlockPos chair = customer.getTargetChair();
            if (chair != null) reserved.add(chair.immutable());
        }
        return reserved;
    }

    public static boolean isChairFree(ServerLevel level, RestaurantHeartBlockEntity heart, BlockPos chairPos) {
        if (!level.getBlockState(chairPos).is(ModContent.CHAIR.get())) return false;
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                new AABB(chairPos).inflate(2.0D),
                entity -> entity.belongsTo(heart.getBlockPos())
        )) {
            if (chairPos.equals(customer.getTargetChair()) && !customer.isLeaving()) return false;
        }
        return true;
    }

    public static void removeCustomersForHeart(ServerLevel level, BlockPos heartPos) {
        for (CustomerEntity customer : level.getEntitiesOfClass(
                CustomerEntity.class,
                new AABB(
                        heartPos.getX() - 128.0D, level.getMinBuildHeight(), heartPos.getZ() - 128.0D,
                        heartPos.getX() + 129.0D, level.getMaxBuildHeight(), heartPos.getZ() + 129.0D
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
