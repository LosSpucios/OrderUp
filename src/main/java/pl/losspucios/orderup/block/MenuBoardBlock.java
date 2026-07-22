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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;
import pl.losspucios.orderup.blockentity.MenuBoardBlockEntity;
import pl.losspucios.orderup.blockentity.RestaurantHeartBlockEntity;
import pl.losspucios.orderup.network.OrderUpNetworking;
import pl.losspucios.orderup.restaurant.RestaurantManager;

public class MenuBoardBlock extends BaseEntityBlock {
    public static final MapCodec<MenuBoardBlock> CODEC = simpleCodec(MenuBoardBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MenuBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof MenuBoardBlockEntity menu
                && RestaurantManager.findContaining(level, pos).orElse(null) instanceof RestaurantHeartBlockEntity heart) {
            menu.setRestaurantHeartPos(heart.getBlockPos());
            heart.setMenuBoardPos(pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MenuBoardBlockEntity menu)) return InteractionResult.PASS;
        RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
        if (heart == null || !heart.isMember(player.getUUID())) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.orderup.not_restaurant_member"), true);
            return InteractionResult.CONSUME;
        }
        OrderUpNetworking.sendMenuData(serverPlayer, menu);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
            if (heart != null && pos.equals(heart.getMenuBoardPos())) heart.setMenuBoardPos(null);
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
        return new MenuBoardBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }
}
