package net.spucio.orderup.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.ModTags;
import net.spucio.orderup.price.PriceCalculator;
import net.spucio.orderup.restaurant.RestaurantManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MenuBoardBlockEntity extends BlockEntity {
    public static final int FOOD_SLOTS = 4;
    public static final int DRINK_SLOTS = 2;
    public static final int SLOT_COUNT = FOOD_SLOTS + DRINK_SLOTS;

    private final List<ItemStack> ghostItems = new ArrayList<>(Collections.nCopies(SLOT_COUNT, ItemStack.EMPTY));
    private BlockPos restaurantHeartPos;

    public MenuBoardBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MENU_BOARD_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            RestaurantHeartBlockEntity heart = resolveRestaurantHeart(serverLevel);
            if (heart != null) {
                RestaurantManager.synchronizeMenuBoards(serverLevel, heart, this);
            }
        }
    }

    public ItemStack getGhostItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        return ghostItems.get(slot).copy();
    }

    public List<ItemStack> getGhostItems() {
        return ghostItems.stream().map(ItemStack::copy).toList();
    }

    /**
     * Changes one menu slot. On the server this is a restaurant-wide operation:
     * every Menu Board linked to the same Restaurant Heart receives the exact
     * same six ghost items.
     */
    public boolean setGhostItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;

        ItemStack normalized = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (level instanceof ServerLevel serverLevel) {
            RestaurantHeartBlockEntity heart = resolveRestaurantHeart(serverLevel);
            if (heart != null) {
                restaurantHeartPos = heart.getBlockPos().immutable();
                return RestaurantManager.setSharedMenuItem(serverLevel, heart, this, slot, normalized);
            }
        }

        if (!isValidMenuChange(ghostItems, slot, normalized)) return false;
        List<ItemStack> updated = getGhostItems();
        updated.set(slot, normalized);
        applySharedMenu(updated, restaurantHeartPos);
        return true;
    }

    /**
     * Applies a complete restaurant menu without starting another propagation.
     * Called by RestaurantManager for all Menu Boards in the same restaurant.
     */
    public void applySharedMenu(List<ItemStack> items, BlockPos heartPos) {
        boolean changed = !Objects.equals(restaurantHeartPos, heartPos);
        restaurantHeartPos = heartPos == null ? null : heartPos.immutable();

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack incoming = slot < items.size() && !items.get(slot).isEmpty()
                    ? items.get(slot).copyWithCount(1)
                    : ItemStack.EMPTY;
            ItemStack current = ghostItems.get(slot);
            if (!sameGhostItem(current, incoming)) {
                ghostItems.set(slot, incoming);
                changed = true;
            }
        }

        if (changed) {
            setChanged();
            syncToClient();
        }
    }

    public static boolean isValidMenuChange(List<ItemStack> currentItems, int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return false;
        if (stack.isEmpty()) return true;
        if (slot < FOOD_SLOTS && !isFood(stack)) return false;
        if (slot >= FOOD_SLOTS && !isDrink(stack)) return false;

        for (int i = 0; i < SLOT_COUNT; i++) {
            if (i == slot || i >= currentItems.size()) continue;
            ItemStack existing = currentItems.get(i);
            if (!existing.isEmpty() && existing.getItem() == stack.getItem()) return false;
        }
        return true;
    }

    private static boolean sameGhostItem(ItemStack first, ItemStack second) {
        if (first.isEmpty() || second.isEmpty()) return first.isEmpty() == second.isEmpty();
        return first.getItem() == second.getItem();
    }

    public boolean isFull() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (ghostItems.get(slot).isEmpty()) return false;
        }
        return true;
    }

    public int getFilledSlotCount() {
        int filled = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!ghostItems.get(slot).isEmpty()) filled++;
        }
        return filled;
    }

    public int getPrice(int slot, ServerLevel level) {
        return PriceCalculator.calculate(level, getGhostItem(slot));
    }

    public static boolean isFood(ItemStack stack) {
        return !stack.isEmpty() && stack.has(DataComponents.FOOD);
    }

    public static boolean isDrink(ItemStack stack) {
        return !stack.isEmpty() && (stack.getUseAnimation() == UseAnim.DRINK || stack.is(ModTags.DRINKS));
    }

    public BlockPos getRestaurantHeartPos() {
        return restaurantHeartPos;
    }

    public void setRestaurantHeartPos(BlockPos pos) {
        BlockPos newPos = pos == null ? null : pos.immutable();
        if (Objects.equals(restaurantHeartPos, newPos)) return;
        restaurantHeartPos = newPos;
        setChanged();
    }

    private RestaurantHeartBlockEntity resolveRestaurantHeart(ServerLevel serverLevel) {
        RestaurantHeartBlockEntity linked = restaurantHeartPos == null
                ? null
                : RestaurantManager.get(serverLevel, restaurantHeartPos).orElse(null);
        return linked != null
                ? linked
                : RestaurantManager.findContaining(serverLevel, worldPosition).orElse(null);
    }

    private void syncToClient() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (restaurantHeartPos != null) tag.putLong("RestaurantHeart", restaurantHeartPos.asLong());
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack stack = ghostItems.get(i);
            if (!stack.isEmpty()) tag.putString("Slot" + i, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        restaurantHeartPos = tag.contains("RestaurantHeart") ? BlockPos.of(tag.getLong("RestaurantHeart")) : null;
        for (int i = 0; i < SLOT_COUNT; i++) {
            String key = "Slot" + i;
            if (!tag.contains(key)) {
                ghostItems.set(i, ItemStack.EMPTY);
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
            Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
            ghostItems.set(i, item == null || item == net.minecraft.world.item.Items.AIR
                    ? ItemStack.EMPTY
                    : new ItemStack(item));
        }
    }
}
