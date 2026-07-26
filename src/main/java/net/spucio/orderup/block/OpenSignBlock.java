package net.spucio.orderup.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.spucio.orderup.blockentity.OpenSignBlockEntity;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.restaurant.RestaurantManager;

public class OpenSignBlock extends BaseEntityBlock {
    public static final MapCodec<OpenSignBlock> CODEC = simpleCodec(OpenSignBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    private static final VoxelShape FLOOR_NORTH = Shapes.or(
            Block.box(2, 6, 6, 14, 15, 10),
            Block.box(7, 0, 7, 9, 6, 9),
            Block.box(4, 0, 5, 12, 1, 11)
    );
    private static final VoxelShape FLOOR_EAST = rotateClockwise(FLOOR_NORTH);
    private static final VoxelShape FLOOR_SOUTH = rotateClockwise(FLOOR_EAST);
    private static final VoxelShape FLOOR_WEST = rotateClockwise(FLOOR_SOUTH);

    private static final VoxelShape WALL_NORTH = Block.box(2, 3, 13, 14, 14, 16);
    private static final VoxelShape WALL_EAST = Block.box(0, 3, 2, 3, 14, 14);
    private static final VoxelShape WALL_SOUTH = Block.box(2, 3, 0, 14, 14, 3);
    private static final VoxelShape WALL_WEST = Block.box(13, 3, 2, 16, 14, 14);

    @SuppressWarnings("this-escape")
    public OpenSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(OPEN, false)
                .setValue(WALL, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clickedFace = context.getClickedFace();
        BlockState state;

        if (clickedFace.getAxis().isHorizontal()) {
            state = defaultBlockState()
                    .setValue(FACING, clickedFace)
                    .setValue(WALL, true)
                    .setValue(OPEN, false);
        } else if (clickedFace == Direction.UP) {
            state = defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(WALL, false)
                    .setValue(OPEN, false);
        } else {
            return null;
        }

        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos supportPos = state.getValue(WALL)
                ? pos.relative(state.getValue(FACING).getOpposite())
                : pos.below();
        Direction supportFace = state.getValue(WALL) ? state.getValue(FACING) : Direction.UP;
        return level.getBlockState(supportPos).isFaceSturdy(level, supportPos, supportFace);
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
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;

        RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
        if (heart != null) {
            heart.setOpenSignPos(pos);
            heart.setOpen(false);
            if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                heart.syncHudNow(serverLevel);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
        if (heart == null || !heart.isMember(player.getUUID())) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("message.orderup.not_restaurant_member"),
                    true
            );
            return InteractionResult.CONSUME;
        }

        boolean open = !state.getValue(OPEN);
        level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
        heart.setOpenSignPos(pos);
        heart.setOpen(open);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            heart.syncHudNow(serverLevel);
        }
        player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                        open ? "message.orderup.restaurant_open" : "message.orderup.restaurant_closed"
                ),
                true
        );
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            RestaurantHeartBlockEntity heart = RestaurantManager.findContaining(level, pos).orElse(null);
            if (heart != null && pos.equals(heart.getOpenSignPos())) {
                heart.setOpenSignPos(null);
                heart.setOpen(false);
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    heart.syncHudNow(serverLevel);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        if (state.getValue(WALL)) {
            return switch (facing) {
                case NORTH -> WALL_NORTH;
                case EAST -> WALL_EAST;
                case SOUTH -> WALL_SOUTH;
                case WEST -> WALL_WEST;
                default -> WALL_NORTH;
            };
        }
        return switch (facing) {
            case NORTH -> FLOOR_NORTH;
            case EAST -> FLOOR_EAST;
            case SOUTH -> FLOOR_SOUTH;
            case WEST -> FLOOR_WEST;
            default -> FLOOR_NORTH;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, WALL);
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
