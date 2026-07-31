package net.spucio.orderup.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.stats.Stats;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.network.OrderUpNetworking;
import net.spucio.orderup.restaurant.RestaurantManager;
import net.spucio.orderup.util.MoneyFormatter;

import java.util.List;

public class RestaurantHeartBlock extends BaseEntityBlock {
    public static final MapCodec<RestaurantHeartBlock> CODEC = simpleCodec(RestaurantHeartBlock::new);

    public RestaurantHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof RestaurantHeartBlockEntity heart)) return InteractionResult.PASS;

        if (!heart.isMember(player.getUUID())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.orderup.not_restaurant_member"), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) {
            OrderUpNetworking.toggleBorder(serverPlayer, heart);
        } else {
            heart.sendSnapshot(serverPlayer);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof RestaurantHeartBlockEntity heart) {
            heart.initializeOwner(player);
        }
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity,
            ItemStack tool
    ) {
        if (level instanceof ServerLevel serverLevel
                && blockEntity instanceof RestaurantHeartBlockEntity heart) {
            player.awardStat(Stats.BLOCK_MINED.get(this));
            player.causeFoodExhaustion(0.005F);
            ItemStack heartStack = new ItemStack(ModContent.RESTAURANT_HEART_ITEM.get());
            heart.saveToItem(heartStack, serverLevel.registryAccess());
            heartStack.set(
                    DataComponents.LORE,
                    new ItemLore(List.of(
                            loreLine("Members: ", Integer.toString(heart.getMembers().size()), ChatFormatting.GRAY),
                            loreLine("Money: ", MoneyFormatter.withDollarSuffix(heart.getMoney()), ChatFormatting.GREEN),
                            loreLine("Level: ", Integer.toString(heart.getRestaurantLevel()), ChatFormatting.GOLD)
                    ))
            );
            popResource(level, pos, heartStack);
            return;
        }

        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            RestaurantManager.removeCustomersForHeart(serverLevel, pos);
            OrderUpNetworking.sendRestaurantRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    private static MutableComponent loreLine(String label, String value, ChatFormatting valueColor) {
        return Component.empty()
                .append(Component.literal(label).withStyle(style ->
                        style.withColor(ChatFormatting.GRAY).withItalic(false)))
                .append(Component.literal(value).withStyle(style ->
                        style.withColor(valueColor).withItalic(false)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RestaurantHeartBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModContent.RESTAURANT_HEART_BE.get(),
                (world, blockPos, blockState, heart) -> RestaurantHeartBlockEntity.serverTick((ServerLevel) world, blockPos, blockState, heart));
    }
}
