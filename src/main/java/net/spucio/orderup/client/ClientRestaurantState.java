package net.spucio.orderup.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.spucio.orderup.util.MoneyFormatter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ClientRestaurantState {
    private static final long HUD_TIMEOUT_TICKS = 40L;
    private static final double BORDER_MAX_AXIS_DISTANCE = 48.0D;

    private static BlockPos hudHeart;
    private static final LinkedHashSet<Long> hudChunks = new LinkedHashSet<>();
    private static long money;
    private static int xp;
    private static int level;
    private static int nextXp = 100;
    private static int occupiedChairs;
    private static int totalChairs;
    private static boolean menuComplete;
    private static boolean openSignPresent;
    private static boolean restaurantOpen;
    private static long lastHudTick = Long.MIN_VALUE;

    private static BlockPos borderHeart;
    private static final LinkedHashSet<Long> borderChunks = new LinkedHashSet<>();
    private static int borderLevel = 1;
    private static long borderMoney;
    private static boolean borderOwner;

    private ClientRestaurantState() {}

    public static void updateHud(
            BlockPos heartPos,
            long newMoney,
            int newXp,
            int newLevel,
            int newNextXp,
            int newOccupiedChairs,
            int newTotalChairs,
            boolean newMenuComplete,
            boolean newOpenSignPresent,
            boolean newRestaurantOpen,
            List<Long> claimedChunks
    ) {
        hudHeart = heartPos;
        money = newMoney;
        xp = newXp;
        level = newLevel;
        nextXp = Math.max(1, newNextXp);
        occupiedChairs = Math.max(0, newOccupiedChairs);
        totalChairs = Math.max(0, newTotalChairs);
        menuComplete = newMenuComplete;
        openSignPresent = newOpenSignPresent;
        restaurantOpen = newOpenSignPresent && newRestaurantOpen;
        hudChunks.clear();
        hudChunks.addAll(claimedChunks);

        if (heartPos.equals(borderHeart)) {
            borderChunks.clear();
            borderChunks.addAll(claimedChunks);
            borderLevel = newLevel;
            borderMoney = newMoney;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            lastHudTick = minecraft.level.getGameTime();
        }
    }

    public static boolean hudActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || hudHeart == null) {
            return false;
        }
        if (minecraft.level.getGameTime() - lastHudTick > HUD_TIMEOUT_TICKS) {
            return false;
        }
        BlockPos playerPos = minecraft.player.blockPosition();
        return hudChunks.contains(ChunkPos.asLong(playerPos));
    }

    public static void applyBorderData(
            BlockPos heartPos,
            List<Long> claimedChunks,
            int restaurantLevel,
            long restaurantMoney,
            boolean owner,
            boolean toggle
    ) {
        if (toggle && heartPos.equals(borderHeart)) {
            clearBorder();
            return;
        }
        if (!toggle && !heartPos.equals(borderHeart)) {
            return;
        }

        borderHeart = heartPos;
        borderChunks.clear();
        borderChunks.addAll(claimedChunks);
        borderLevel = Math.max(1, restaurantLevel);
        borderMoney = Math.max(0L, restaurantMoney);
        borderOwner = owner;
    }

    public static boolean borderActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (borderHeart == null || minecraft.player == null || minecraft.level == null) return false;

        double centerX = borderHeart.getX() + 0.5D;
        double centerZ = borderHeart.getZ() + 0.5D;
        if (Math.abs(minecraft.player.getX() - centerX) > BORDER_MAX_AXIS_DISTANCE
                || Math.abs(minecraft.player.getZ() - centerZ) > BORDER_MAX_AXIS_DISTANCE) {
            clearBorder();
            return false;
        }
        return true;
    }

    private static void clearBorder() {
        borderHeart = null;
        borderChunks.clear();
        borderLevel = 1;
        borderMoney = 0L;
        borderOwner = false;
    }

    public static List<BoundaryEdge> borderEdges() {
        if (!borderActive()) return List.of();
        Set<Long> claims = Set.copyOf(borderChunks);
        List<BoundaryEdge> edges = new ArrayList<>();
        for (long key : borderChunks) {
            int chunkX = ChunkPos.getX(key);
            int chunkZ = ChunkPos.getZ(key);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                long neighbour = ChunkPos.asLong(
                        chunkX + direction.getStepX(),
                        chunkZ + direction.getStepZ()
                );
                if (!claims.contains(neighbour)) {
                    edges.add(new BoundaryEdge(chunkX, chunkZ, direction));
                }
            }
        }
        return edges;
    }

    public static ExpansionTarget findLookedAtExpansion(
            Vec3 origin,
            Vec3 lookDirection,
            double maxDistance,
            int minBuildHeight,
            int maxBuildHeight
    ) {
        if (!borderActive() || !borderOwner) return null;

        BoundaryEdge closestEdge = null;
        double closestDistance = Double.MAX_VALUE;
        final double epsilon = 0.001D;

        for (BoundaryEdge edge : borderEdges()) {
            ChunkPos chunk = new ChunkPos(edge.chunkX(), edge.chunkZ());
            double distance;
            double hitX;
            double hitY;
            double hitZ;

            switch (edge.direction()) {
                case EAST, WEST -> {
                    if (Math.abs(lookDirection.x) < 1.0E-7D) continue;
                    double planeX = edge.direction() == Direction.EAST
                            ? chunk.getMaxBlockX() + 1.0D
                            : chunk.getMinBlockX();
                    distance = (planeX - origin.x) / lookDirection.x;
                    if (distance <= 0.05D || distance > maxDistance) continue;
                    hitX = planeX;
                    hitY = origin.y + lookDirection.y * distance;
                    hitZ = origin.z + lookDirection.z * distance;
                    if (hitZ < chunk.getMinBlockZ() - epsilon
                            || hitZ > chunk.getMaxBlockZ() + 1.0D + epsilon
                            || hitY < minBuildHeight
                            || hitY > maxBuildHeight) continue;
                }
                case NORTH, SOUTH -> {
                    if (Math.abs(lookDirection.z) < 1.0E-7D) continue;
                    double planeZ = edge.direction() == Direction.SOUTH
                            ? chunk.getMaxBlockZ() + 1.0D
                            : chunk.getMinBlockZ();
                    distance = (planeZ - origin.z) / lookDirection.z;
                    if (distance <= 0.05D || distance > maxDistance) continue;
                    hitX = origin.x + lookDirection.x * distance;
                    hitY = origin.y + lookDirection.y * distance;
                    hitZ = planeZ;
                    if (hitX < chunk.getMinBlockX() - epsilon
                            || hitX > chunk.getMaxBlockX() + 1.0D + epsilon
                            || hitY < minBuildHeight
                            || hitY > maxBuildHeight) continue;
                }
                default -> {
                    continue;
                }
            }

            if (distance < closestDistance) {
                closestDistance = distance;
                closestEdge = edge;
            }
        }

        if (closestEdge == null) return null;
        return new ExpansionTarget(
                closestEdge.targetChunkX(),
                closestEdge.targetChunkZ(),
                closestDistance
        );
    }

    public static boolean canSeeCustomerThoughts(BlockPos heartPos) {
        return hudActive() && heartPos != null && heartPos.equals(hudHeart);
    }

    public static int requiredLevelForNextChunk() {
        return Math.max(1, borderChunks.size());
    }

    public static long nextChunkPrice() {
        int requiredLevel = requiredLevelForNextChunk();
        return (long) requiredLevel * 100L;
    }

    public static String expansionLabel() {
        int requiredLevel = requiredLevelForNextChunk();
        return borderLevel < requiredLevel
                ? "req. level " + requiredLevel
                : nextChunkPrice() + "$";
    }

    public static int expansionLabelColor() {
        int requiredLevel = requiredLevelForNextChunk();
        return borderLevel >= requiredLevel
                && borderMoney >= MoneyFormatter.dollarsToHalfUnits(nextChunkPrice())
                ? 0xFF55FF55
                : 0xFFFF5555;
    }

    public static long money() { return money; }
    public static int xp() { return xp; }
    public static int level() { return level; }
    public static int nextXp() { return nextXp; }
    public static int occupiedChairs() { return occupiedChairs; }
    public static int totalChairs() { return totalChairs; }
    public static boolean menuComplete() { return menuComplete; }
    public static boolean openSignPresent() { return openSignPresent; }
    public static boolean restaurantOpen() { return restaurantOpen; }
    public static BlockPos borderHeart() { return borderHeart; }
    public static boolean borderOwner() { return borderOwner; }

    public record BoundaryEdge(int chunkX, int chunkZ, Direction direction) {
        public int targetChunkX() { return chunkX + direction.getStepX(); }
        public int targetChunkZ() { return chunkZ + direction.getStepZ(); }
    }

    public record ExpansionTarget(int chunkX, int chunkZ, double distance) {}
}
