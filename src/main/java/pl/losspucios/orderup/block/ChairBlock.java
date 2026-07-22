package pl.losspucios.orderup.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import pl.losspucios.orderup.ModContent;

import java.util.Optional;

public class ChairBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2, 0, 2, 14, 8, 14),
            Block.box(2, 8, 12, 14, 16, 14)
    );

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
                if (!other.equals(chairPos) && level.getBlockState(other).is(ModContent.CHAIR.get())) existingChairs++;
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
    protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return level.getBlockState(pos.relative(facing)).is(ModContent.TABLE.get());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
