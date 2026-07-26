package net.spucio.orderup.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TableBlock extends Block {
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1.0D, 11.0D, 1.0D, 15.0D, 15.0D, 15.0D),
            Block.box(2.0D, 0.0D, 2.0D, 4.0D, 11.0D, 4.0D),
            Block.box(12.0D, 0.0D, 2.0D, 14.0D, 11.0D, 4.0D),
            Block.box(2.0D, 0.0D, 12.0D, 4.0D, 11.0D, 14.0D),
            Block.box(12.0D, 0.0D, 12.0D, 14.0D, 11.0D, 14.0D)
    );

    public TableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
