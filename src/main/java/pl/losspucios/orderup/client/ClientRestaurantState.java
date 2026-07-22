package pl.losspucios.orderup.client;

import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;

public final class ClientRestaurantState {
    private static int hudRadius;
    private static int hudVertical = 8;
    private static BlockPos hudHeart;
    private static long money;
    private static int xp;
    private static int level;
    private static int nextXp = 100;
    private static long lastHudTick = Long.MIN_VALUE;

    private static BlockPos borderHeart;
    private static int borderRadius;

    private ClientRestaurantState() {}

    public static void updateHud(BlockPos heartPos, long newMoney, int newXp, int newLevel, int newNextXp) {
        hudHeart = heartPos;
        money = newMoney;
        xp = newXp;
        level = newLevel;
        nextXp = Math.max(1, newNextXp);

        hudRadius = 4 + newLevel * 4;
        hudVertical = 8;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) lastHudTick = minecraft.level.getGameTime();
    }

    public static boolean hudActive() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || hudHeart == null) {
            return false;
        }

        if (minecraft.level.getGameTime() - lastHudTick > 40L) {
            return false;
        }

        BlockPos playerPos = minecraft.player.blockPosition();

        return Math.abs(playerPos.getX() - hudHeart.getX()) <= hudRadius
                && Math.abs(playerPos.getZ() - hudHeart.getZ()) <= hudRadius
                && Math.abs(playerPos.getY() - hudHeart.getY()) <= hudVertical;
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

    public static BlockPos hudHeart() { return hudHeart; }
    public static long money() { return money; }
    public static int xp() { return xp; }
    public static int level() { return level; }
    public static int nextXp() { return nextXp; }
    public static BlockPos borderHeart() { return borderHeart; }
    public static int borderRadius() { return borderRadius; }
}
