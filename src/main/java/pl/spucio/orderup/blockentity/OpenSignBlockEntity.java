package pl.spucio.orderup.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pl.spucio.orderup.ModContent;

public class OpenSignBlockEntity extends BlockEntity {
    public OpenSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.OPEN_SIGN_BE.get(), pos, state);
    }
}
