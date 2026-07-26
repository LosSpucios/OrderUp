package pl.spucio.orderup.item;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import pl.spucio.orderup.block.ChairBlock;
import pl.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import pl.spucio.orderup.restaurant.RestaurantManager;

import java.util.Optional;

public class RestaurantRestrictedBlockItem extends BlockItem {
    public enum Kind { TABLE, CHAIR, MENU, OPEN_SIGN }

    private final Kind kind;

    public RestaurantRestrictedBlockItem(Block block, Properties properties, Kind kind) {
        super(block, properties);
        this.kind = kind;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return super.place(context);
        }

        BlockPos placementPos = context.getClickedPos();
        Optional<RestaurantHeartBlockEntity> optionalHeart = RestaurantManager.findContaining(level, placementPos);
        if (optionalHeart.isEmpty()) {
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("message.orderup.only_inside_restaurant"), true);
            return InteractionResult.FAIL;
        }

        RestaurantHeartBlockEntity heart = optionalHeart.get();
        if (kind == Kind.OPEN_SIGN && heart.getOpenSignPos() != null) {
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("message.orderup.only_one_open_sign"), true);
            return InteractionResult.FAIL;
        }

        if (kind == Kind.CHAIR && ChairBlock.findAttachableTable(level, placementPos).isEmpty()) {
            if (context.getPlayer() != null) context.getPlayer().displayClientMessage(Component.translatable("message.orderup.chair_needs_table"), true);
            return InteractionResult.FAIL;
        }

        return super.place(context);
    }
}
