package net.spucio.orderup.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.restaurant.RestaurantManager;

import java.util.Optional;

public class ChairBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Base model faces north: the table is north of the chair and the backrest is south.
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
            Block.box(3.0D, 6.0D, 3.0D, 13.0D, 9.0D, 13.0D),
            Block.box(3.0D, 0.0D, 3.0D, 5.0D, 6.0D, 5.0D),
            Block.box(11.0D, 0.0D, 3.0D, 13.0D, 6.0D, 5.0D),
            Block.box(3.0D, 0.0D, 11.0D, 5.0D, 6.0D, 13.0D),
            Block.box(11.0D, 0.0D, 11.0D, 13.0D, 6.0D, 13.0D),
            Block.box(3.0D, 8.0D, 12.0D, 5.0D, 16.0D, 14.0D),
            Block.box(11.0D, 8.0D, 12.0D, 13.0D, 16.0D, 14.0D),
            Block.box(4.0D, 11.0D, 12.0D, 12.0D, 14.0D, 14.0D)
    );
    private static final VoxelShape EAST_SHAPE = rotateClockwise(NORTH_SHAPE);
    private static final VoxelShape SOUTH_SHAPE = rotateClockwise(EAST_SHAPE);
    private static final VoxelShape WEST_SHAPE = rotateClockwise(SOUTH_SHAPE);

    @SuppressWarnings("this-escape")
    public ChairBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public static Optional<Direction> findAttachableTable(LevelAccessor level, BlockPos chairPos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos tablePos = chairPos.relative(direction);
            if (!level.getBlockState(tablePos).is(ModContent.TABLE.get())) continue;

            int existingChairs = 0;
            for (Direction around : Direction.Plane.HORIZONTAL) {
                BlockPos other = tablePos.relative(around);
                if (!other.equals(chairPos) && level.getBlockState(other).is(ModContent.CHAIR.get())) {
                    existingChairs++;
                }
            }
            if (existingChairs == 0) return Optional.of(direction);
        }
        return Optional.empty();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return findAttachableTable(context.getLevel(), context.getClickedPos())
                .map(direction -> defaultBlockState().setValue(FACING, direction))
                .orElse(null);
    }


    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            RestaurantManager.findContaining(level, pos).ifPresent(heart -> heart.syncHudNow(serverLevel));
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        RestaurantHeartBlockEntity heart = !level.isClientSide
                ? RestaurantManager.findContaining(level, pos).orElse(null)
                : null;
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock())
                && heart != null
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            heart.syncHudNow(serverLevel);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return level.getBlockState(pos.relative(facing)).is(ModContent.TABLE.get());
    }

    @Override
    protected BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos
    ) {
        return canSurvive(state, level, pos)
                ? super.updateShape(state, direction, neighborState, level, pos, neighborPos)
                : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state.getValue(FACING));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    private static VoxelShape shapeFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> NORTH_SHAPE;
        };
    }

    private static VoxelShape rotateClockwise(VoxelShape shape) {
        VoxelShape[] rotated = {Shapes.empty()};
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                rotated[0] = Shapes.or(
                        rotated[0],
                        Shapes.box(1.0D - maxZ, minY, minX, 1.0D - minZ, maxY, maxX)
                )
        );
        return rotated[0];
    }
}
