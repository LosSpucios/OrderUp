package pl.losspucios.orderup.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import pl.losspucios.orderup.blockentity.OpenSignBlockEntity;
import pl.losspucios.orderup.blockentity.RestaurantHeartBlockEntity;
import pl.losspucios.orderup.restaurant.RestaurantManager;

public class OpenSignBlock extends BaseEntityBlock {
    public static final MapCodec<OpenSignBlock> CODEC = simpleCodec(OpenSignBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    public OpenSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, true));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(OPEN, true);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
            if (heart != null) {
                heart.setOpenSignPos(pos);
                heart.setOpen(true);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
        if (heart == null || !heart.isMember(player.getUUID())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.orderup.not_restaurant_member"), true);
            return InteractionResult.CONSUME;
        }
        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), 3);
        heart.setOpen(open);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(open ? "message.orderup.restaurant_open" : "message.orderup.restaurant_closed"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
            if (heart != null && pos.equals(heart.getOpenSignPos())) {
                heart.setOpenSignPos(null);
                heart.setOpen(true);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OpenSignBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }
}
