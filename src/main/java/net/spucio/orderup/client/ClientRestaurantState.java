package net.spucio.orderup.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

public final class ClientRestaurantState {
    private static final int HUD_VERTICAL_RANGE = 8;
    private static final long HUD_TIMEOUT_TICKS = 40L;

    private static BlockPos hudHeart;
    private static int hudRadius;
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
    private static int borderRadius;

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
            boolean newRestaurantOpen
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
        hudRadius = 4 + newLevel * 4;

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
        return Math.abs(playerPos.getX() - hudHeart.getX()) <= hudRadius
                && Math.abs(playerPos.getZ() - hudHeart.getZ()) <= hudRadius
                && Math.abs(playerPos.getY() - hudHeart.getY()) <= HUD_VERTICAL_RANGE;
    }

    public static void toggleBorder(BlockPos heartPos, int radius) {
        if (heartPos.equals(borderHeart)) {
            borderHeart = null;
            borderRadius = 0;
        } else {
            borderHeart = heartPos;
            borderRadius = radius;
        }
    }

    public static boolean canSeeCustomerThoughts(BlockPos heartPos) {
        return hudActive() && heartPos != null && heartPos.equals(hudHeart);
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
    public static int borderRadius() { return borderRadius; }
}
