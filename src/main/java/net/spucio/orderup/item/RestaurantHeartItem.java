package net.spucio.orderup.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.spucio.orderup.restaurant.RestaurantManager;

public class RestaurantHeartItem extends BlockItem {
    public RestaurantHeartItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (context.getLevel() instanceof ServerLevel level
                && overlapsExistingRestaurant(level, context)) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(
                        Component.literal("This Restaurant Heart would overlap another restaurant."),
                        true
                );
            }
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    private static boolean overlapsExistingRestaurant(ServerLevel level, BlockPlaceContext context) {
        BlockPos targetPos = context.getClickedPos();
        int targetHeartChunkX = targetPos.getX() >> 4;
        int targetHeartChunkZ = targetPos.getZ() >> 4;

        CustomData blockEntityData = context.getItemInHand().getOrDefault(
                DataComponents.BLOCK_ENTITY_DATA,
                CustomData.EMPTY
        );
        CompoundTag tag = blockEntityData.copyTag();
        long[] storedClaims = tag.getLongArray("ClaimedChunks");

        if (storedClaims.length == 0) {
            return RestaurantManager.isChunkClaimedByOther(
                    level,
                    null,
                    targetHeartChunkX,
                    targetHeartChunkZ
            );
        }

        int storedHeartX = tag.contains("StoredHeartX", Tag.TAG_INT)
                ? tag.getInt("StoredHeartX")
                : targetPos.getX();
        int storedHeartZ = tag.contains("StoredHeartZ", Tag.TAG_INT)
                ? tag.getInt("StoredHeartZ")
                : targetPos.getZ();
        int chunkDeltaX = targetHeartChunkX - (storedHeartX >> 4);
        int chunkDeltaZ = targetHeartChunkZ - (storedHeartZ >> 4);

        for (long storedClaim : storedClaims) {
            int chunkX = ChunkPos.getX(storedClaim) + chunkDeltaX;
            int chunkZ = ChunkPos.getZ(storedClaim) + chunkDeltaZ;
            if (RestaurantManager.isChunkClaimedByOther(level, null, chunkX, chunkZ)) {
                return true;
            }
        }
        return false;
    }
}
