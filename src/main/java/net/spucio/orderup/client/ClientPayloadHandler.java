package net.spucio.orderup.client;

import net.minecraft.client.Minecraft;
import net.spucio.orderup.network.OrderUpNetworking;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleHeartData(OrderUpNetworking.HeartDataPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof RestaurantHeartScreen screen && screen.getHeartPos().equals(payload.heartPos())) {
            screen.applyPayload(payload);
        } else {
            minecraft.setScreen(new RestaurantHeartScreen(payload));
        }
    }

    public static void handleMenuData(OrderUpNetworking.MenuDataPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof MenuBoardScreen screen && screen.getMenuPos().equals(payload.menuPos())) {
            screen.applyPayload(payload);
        } else {
            minecraft.setScreen(new MenuBoardScreen(payload));
        }
    }

    public static void handleHud(OrderUpNetworking.HudPayload payload) {
        ClientRestaurantState.updateHud(
                payload.heartPos(),
                payload.money(),
                payload.xp(),
                payload.level(),
                payload.nextXp(),
                payload.occupiedChairs(),
                payload.totalChairs(),
                payload.menuComplete(),
                payload.openSignPresent(),
                payload.restaurantOpen(),
                payload.claimedChunks()
        );
    }

    public static void handleRestaurantRemoved(OrderUpNetworking.RestaurantRemovedPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean wasTracked = ClientRestaurantState.isTrackingRestaurant(payload.heartPos());
        ClientRestaurantState.removeRestaurant(payload.heartPos());
        if (minecraft.screen instanceof RestaurantHeartScreen screen
                && screen.getHeartPos().equals(payload.heartPos())) {
            minecraft.setScreen(null);
        } else if (wasTracked && minecraft.screen instanceof MenuBoardScreen) {
            minecraft.setScreen(null);
        }
    }

    public static void handleBorderData(OrderUpNetworking.BorderDataPayload payload) {
        ClientRestaurantState.applyBorderData(
                payload.heartPos(),
                payload.claimedChunks(),
                payload.blockedChunks(),
                payload.level(),
                payload.money(),
                payload.owner(),
                payload.toggle()
        );
    }
}
