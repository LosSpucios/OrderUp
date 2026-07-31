package net.spucio.orderup.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.spucio.orderup.ModContent;
import net.spucio.orderup.ModParticles;
import net.spucio.orderup.block.ChairBlock;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.restaurant.RestaurantManager;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class CustomerEntity extends PathfinderMob implements VillagerDataHolder {
    public static final int WALKING = 0;
    public static final int THINKING = 1;
    public static final int WAITING = 2;
    public static final int LEAVING = 3;
    public static final int REACTING = 5;

    // In the old save format CustomerState=4 meant angry. Mood is now stored separately.
    private static final int LEGACY_ANGRY_STATE = 4;
    private static final int RESULT_DISPLAY_TICKS = 30;
    private static final double ARMOR_STAND_PASSENGER_HEIGHT = 1.975D;
    private static final double CHAIR_SEAT_HEIGHT = 0.50D;
    private static final double SEAT_ENTITY_Y_OFFSET = CHAIR_SEAT_HEIGHT - ARMOR_STAND_PASSENGER_HEIGHT;
    private static final double SIT_DISTANCE_SQR = 1.35D * 1.35D;
    private static final int MAX_LEAVING_TICKS = 200;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_MOOD = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HEART = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_CHAIR = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<ItemStack> DATA_FOOD = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_DRINK = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_FOOD_DONE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRINK_DONE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ORDER_FAILED = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos menuBoardPos;
    private int orderPrice;
    private int stateTimer;
    private int stuckTicks;
    private int pathFailureTicks;
    private int leavingTicks;
    private double lastProgressX;
    private double lastProgressZ;
    private UUID seatUuid;
    private Direction chairApproachDirection;
    private boolean angryRewardClaimed;
    private int traversalJumpCooldown;
    private int blockedTraversalTicks;
    private boolean climbingToChair;
    private VillagerData villagerData = new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1);

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.STEP_HEIGHT, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        /*
         * The restaurant state machine still owns movement targets. This small
         * vanilla goal only handles wooden doors encountered by the active path;
         * true means the customer closes the door after passing through it.
         */
        if (navigation instanceof GroundPathNavigation groundNavigation) {
            groundNavigation.setCanPassDoors(true);
            groundNavigation.setCanOpenDoors(true);
        }
        goalSelector.addGoal(0, new OpenDoorGoal(this, true));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, WALKING);
        builder.define(DATA_MOOD, CustomerMood.NEUTRAL.id());
        builder.define(DATA_HEART, Optional.empty());
        builder.define(DATA_CHAIR, Optional.empty());
        builder.define(DATA_FOOD, ItemStack.EMPTY);
        builder.define(DATA_DRINK, ItemStack.EMPTY);
        builder.define(DATA_FOOD_DONE, false);
        builder.define(DATA_DRINK_DONE, false);
        builder.define(DATA_ORDER_FAILED, false);
    }

    public void setRestaurantContext(BlockPos heartPos, BlockPos chairPos, BlockPos menuPos) {
        entityData.set(DATA_HEART, Optional.of(heartPos.immutable()));
        entityData.set(DATA_CHAIR, Optional.of(chairPos.immutable()));
        menuBoardPos = menuPos == null ? null : menuPos.immutable();
        entityData.set(DATA_STATE, WALKING);
        entityData.set(DATA_FOOD, ItemStack.EMPTY);
        entityData.set(DATA_DRINK, ItemStack.EMPTY);
        entityData.set(DATA_FOOD_DONE, false);
        entityData.set(DATA_DRINK_DONE, false);
        entityData.set(DATA_ORDER_FAILED, false);
        orderPrice = 0;
        angryRewardClaimed = false;
        pathFailureTicks = 0;
        leavingTicks = 0;
        chairApproachDirection = null;
        traversalJumpCooldown = 0;
        blockedTraversalTicks = 0;
        climbingToChair = false;
        setMood(CustomerMood.NEUTRAL);
    }

    public boolean beginWalkingToChair() {
        BlockPos chair = getTargetChair();
        if (chair == null) return false;

        lastProgressX = getX();
        lastProgressZ = getZ();
        stuckTicks = 0;

        /*
         * The chair itself remains the target. Vanilla navigation may stop on any
         * reachable side; once the customer is roughly one block away,
         * tickWalking snaps them onto their reserved chair.
         */
        Path path = getNavigation().createPath(chair, 0);
        if (path != null) {
            boolean started = getNavigation().moveTo(path, 0.62D);
            if (started) {
                pathFailureTicks = 0;
                return true;
            }
        }

        boolean started = getNavigation().moveTo(
                chair.getX() + 0.5D,
                chair.getY(),
                chair.getZ() + 0.5D,
                0.62D
        );
        if (started) pathFailureTicks = 0;
        return started;
    }

    @Override
    public void tick() {
        super.tick();
        if (traversalJumpCooldown > 0) traversalJumpCooldown--;
        // onClimbable() uses the value prepared during the previous server tick.
        // Reset it now; assistChairTraversal may enable it again for the next tick.
        climbingToChair = false;
        if (level().isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level();
        BlockPos heartPos = getRestaurantHeart();

        // A manually summoned neutral customer is persistent and independent.
        if (heartPos == null) return;

        RestaurantHeartBlockEntity heart = RestaurantManager.get(serverLevel, heartPos).orElse(null);
        if (heart == null) {
            if (getMood() == CustomerMood.NEUTRAL) {
                detachFromRestaurant(serverLevel);
            } else {
                cleanupSeat(serverLevel);
                discard();
            }
            return;
        }

        switch (getCustomerState()) {
            case WALKING -> tickWalking(serverLevel, heart);
            case THINKING -> tickThinking(serverLevel, heart);
            case WAITING -> tickWaiting(serverLevel);
            case REACTING -> tickReacting(serverLevel, heart);
            case LEAVING -> tickLeaving(serverLevel, heart);
            default -> entityData.set(DATA_STATE, WALKING);
        }
    }

    private void tickWalking(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (!heart.isOpen()) {
            beginLeaving();
            return;
        }

        BlockPos chair = getTargetChair();
        if (chair == null || !level.getBlockState(chair).is(ModContent.CHAIR.get())) {
            beginLeaving();
            return;
        }

        double chairX = chair.getX() + 0.5D;
        double chairZ = chair.getZ() + 0.5D;
        double dx = getX() - chairX;
        double dz = getZ() - chairZ;
        double horizontalDistanceSqr = dx * dx + dz * dz;
        double verticalDistance = Math.abs(getY() - chair.getY());

        /*
         * No fixed approach side is required. The customer may reach the chair
         * from any direction; being approximately one block from their reserved
         * chair is enough to sit.
         */
        if (horizontalDistanceSqr <= SIT_DISTANCE_SQR && verticalDistance <= 1.6D) {
            rememberApproachDirection(chairX, chairZ);
            sitDown(level, chair);
            return;
        }

        assistChairTraversal(chair, chairX, chairZ, horizontalDistanceSqr);

        if (tickCount % 10 == 0 && getNavigation().isDone()) {
            if (beginWalkingToChair()) {
                pathFailureTicks = 0;
            } else {
                pathFailureTicks += 20;
            }
        }

        trackStuckProgress();
        if (stuckTicks > 160) {
            if (beginWalkingToChair()) {
                pathFailureTicks = 0;
            } else {
                pathFailureTicks += 40;
            }
            stuckTicks = 0;
        }

        if (pathFailureTicks > 400) {
            beginLeaving();
        }
    }

    /**
     * Gives restaurant customers a forgiving route to their reserved chair without turning
     * every walk into constant bunny-hopping. A normal jump is requested only when a solid
     * obstacle roughly one block high is directly in front of the customer and there is enough
     * headroom to land on it. Taller walls use the slower spider-like fallback after the customer
     * has actually been blocked for a short time.
     */
    private void assistChairTraversal(
            BlockPos chair,
            double chairX,
            double chairZ,
            double horizontalDistanceSqr
    ) {
        double towardX = chairX - getX();
        double towardZ = chairZ - getZ();
        double horizontalLength = Math.sqrt(towardX * towardX + towardZ * towardZ);
        boolean blocked = horizontalCollision && horizontalDistanceSqr > SIT_DISTANCE_SQR;

        if (!blocked || horizontalLength <= 0.001D) {
            blockedTraversalTicks = 0;
            return;
        }

        blockedTraversalTicks++;
        boolean oneBlockObstacle = hasOneBlockObstacleAhead(towardX, towardZ, horizontalLength);

        if (oneBlockObstacle && onGround() && traversalJumpCooldown == 0) {
            getJumpControl().jump();
            traversalJumpCooldown = 12;
            return;
        }

        // Do not engage climbing for a normal one-block ledge. It is reserved for a genuinely
        // taller obstruction and starts only after several blocked ticks, avoiding jump/climb
        // oscillation after the customer has already stepped onto a block.
        if (!oneBlockObstacle && blockedTraversalTicks >= 12) {
            climbingToChair = true;
            Vec3 motion = getDeltaMovement();
            double pull = 0.10D;
            setDeltaMovement(
                    motion.x * 0.60D + towardX / horizontalLength * pull,
                    Math.max(motion.y, 0.23D),
                    motion.z * 0.60D + towardZ / horizontalLength * pull
            );
            hasImpulse = true;
            resetFallDistance();
        }
    }

    private boolean hasOneBlockObstacleAhead(
            double towardX,
            double towardZ,
            double horizontalLength
    ) {
        double directionX = towardX / horizontalLength;
        double directionZ = towardZ / horizontalLength;
        BlockPos obstaclePos = BlockPos.containing(
                getX() + directionX * 0.72D,
                getY() + 0.10D,
                getZ() + directionZ * 0.72D
        );

        var obstacleShape = level().getBlockState(obstaclePos).getCollisionShape(level(), obstaclePos);
        if (obstacleShape.isEmpty()) return false;

        double obstacleTop = obstaclePos.getY() + obstacleShape.max(Direction.Axis.Y);
        double obstacleHeightFromFeet = obstacleTop - getY();
        if (obstacleHeightFromFeet < 0.45D || obstacleHeightFromFeet > 1.10D) return false;

        BlockPos landingBody = obstaclePos.above();
        BlockPos landingHead = obstaclePos.above(2);
        return level().getBlockState(landingBody).getCollisionShape(level(), landingBody).isEmpty()
                && level().getBlockState(landingHead).getCollisionShape(level(), landingHead).isEmpty();
    }

    @Override
    public boolean onClimbable() {
        return super.onClimbable() || climbingToChair;
    }

    private void rememberApproachDirection(double chairX, double chairZ) {
        double dx = getX() - chairX;
        double dz = getZ() - chairZ;
        if (Math.abs(dx) >= Math.abs(dz)) {
            chairApproachDirection = dx >= 0.0D ? Direction.EAST : Direction.WEST;
        } else {
            chairApproachDirection = dz >= 0.0D ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private Direction getChairFrontDirection(BlockPos chair) {
        var chairState = level().getBlockState(chair);
        if (chairState.is(ModContent.CHAIR.get()) && chairState.hasProperty(ChairBlock.FACING)) {
            return chairState.getValue(ChairBlock.FACING).getOpposite();
        }
        return Direction.SOUTH;
    }

    private void tickThinking(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (!validChair(level)) {
            beginLeaving();
            return;
        }
        if (--stateTimer <= 0) chooseOrder(level, heart);
    }

    private void tickWaiting(ServerLevel level) {
        if (!validChair(level)) beginLeaving();
    }

    private void tickReacting(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (--stateTimer > 0) return;

        if (getMood() == CustomerMood.HAPPY) {
            heart.addMoney(orderPrice);
            heart.spawnRestaurantXpReward(
                    level,
                    position().add(0.0D, 0.12D, 0.0D),
                    10 + Math.max(1, orderPrice / 2)
            );
            level.sendParticles(
                    ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + 1.4D, getZ(),
                    10, 0.35D, 0.45D, 0.35D, 0.02D
            );
            level.playSound(null, blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.8F, 1.1F);
        }

        standUpFromChair(level);
        entityData.set(DATA_STATE, LEAVING);
        leavingTicks = 0;
        setLeavePath(level, heart);
        heart.syncHudNow(level);
    }

    private void tickLeaving(ServerLevel level, RestaurantHeartBlockEntity heart) {
        leavingTicks++;

        if (!heart.containsPosition(getX(), getZ())) {
            finishLeaving(level);
            return;
        }

        if (getMood() != CustomerMood.NEUTRAL && leavingTicks >= MAX_LEAVING_TICKS) {
            finishLeaving(level);
            return;
        }

        if (tickCount % 20 == 0 && getNavigation().isDone()) {
            setLeavePath(level, heart);
        }

        trackStuckProgress();
        if (stuckTicks > 80) {
            setLeavePath(level, heart);
            stuckTicks = 0;
        }
    }

    private void finishLeaving(ServerLevel level) {
        cleanupSeat(level);
        getNavigation().stop();

        if (getMood() == CustomerMood.NEUTRAL) {
            detachFromRestaurant(level);
        } else {
            discard();
        }
    }

    private void detachFromRestaurant(ServerLevel level) {
        cleanupSeat(level);
        getNavigation().stop();
        entityData.set(DATA_HEART, Optional.empty());
        entityData.set(DATA_CHAIR, Optional.empty());
        entityData.set(DATA_STATE, WALKING);
        entityData.set(DATA_FOOD, ItemStack.EMPTY);
        entityData.set(DATA_DRINK, ItemStack.EMPTY);
        entityData.set(DATA_FOOD_DONE, false);
        entityData.set(DATA_DRINK_DONE, false);
        entityData.set(DATA_ORDER_FAILED, false);
        setMood(CustomerMood.NEUTRAL);
        menuBoardPos = null;
        orderPrice = 0;
        angryRewardClaimed = false;
        stuckTicks = 0;
        pathFailureTicks = 0;
        leavingTicks = 0;
        chairApproachDirection = null;
        traversalJumpCooldown = 0;
        blockedTraversalTicks = 0;
        climbingToChair = false;
    }

    private void sitDown(ServerLevel level, BlockPos chair) {
        getNavigation().stop();
        Direction tableDirection = getChairFrontDirection(chair).getOpposite();
        float seatedYaw = tableDirection.toYRot();
        setYRot(seatedYaw);
        setYHeadRot(seatedYaw);
        yBodyRot = seatedYaw;

        ArmorStand seat = EntityType.ARMOR_STAND.create(level);
        if (seat != null) {
            seat.setInvisible(true);
            seat.setInvulnerable(true);
            seat.setNoGravity(true);
            seat.setYRot(seatedYaw);
            seat.setPos(
                    chair.getX() + 0.5D,
                    chair.getY() + SEAT_ENTITY_Y_OFFSET,
                    chair.getZ() + 0.5D
            );
            level.addFreshEntity(seat);
            seatUuid = seat.getUUID();
            startRiding(seat, true);
        } else {
            setPos(chair.getX() + 0.5D, chair.getY() + CHAIR_SEAT_HEIGHT, chair.getZ() + 0.5D);
        }

        entityData.set(DATA_STATE, THINKING);
        stateTimer = 45 + random.nextInt(46);
        stuckTicks = 0;
    }

    private void chooseOrder(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (menuBoardPos == null
                || !(level.getBlockEntity(menuBoardPos) instanceof MenuBoardBlockEntity menu)
                || !menu.isFull()) {
            beginLeaving();
            return;
        }

        int foodSlot = random.nextInt(MenuBoardBlockEntity.FOOD_SLOTS);
        ItemStack food = menu.getGhostItem(foodSlot);
        ItemStack drink = random.nextFloat() < 0.50F
                ? menu.getGhostItem(MenuBoardBlockEntity.FOOD_SLOTS + random.nextInt(MenuBoardBlockEntity.DRINK_SLOTS))
                : ItemStack.EMPTY;

        entityData.set(DATA_FOOD, food.copyWithCount(1));
        entityData.set(DATA_DRINK, drink.isEmpty() ? ItemStack.EMPTY : drink.copyWithCount(1));
        entityData.set(DATA_FOOD_DONE, false);
        entityData.set(DATA_DRINK_DONE, drink.isEmpty());
        entityData.set(DATA_ORDER_FAILED, false);
        angryRewardClaimed = false;

        orderPrice = menu.getPrice(foodSlot, level);
        if (!drink.isEmpty()) {
            for (int slot = MenuBoardBlockEntity.FOOD_SLOTS; slot < MenuBoardBlockEntity.SLOT_COUNT; slot++) {
                if (ItemStack.isSameItemSameComponents(menu.getGhostItem(slot), drink)) {
                    orderPrice += menu.getPrice(slot, level);
                    break;
                }
            }
        }

        entityData.set(DATA_STATE, WAITING);
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (getCustomerState() != WAITING) return InteractionResult.PASS;
        if (!(level() instanceof ServerLevel serverLevel)) return InteractionResult.PASS;

        BlockPos heartPos = getRestaurantHeart();
        RestaurantHeartBlockEntity heart = heartPos == null
                ? null
                : RestaurantManager.get(serverLevel, heartPos).orElse(null);
        if (heart == null || !heart.isMember(player.getUUID())) return InteractionResult.FAIL;

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return InteractionResult.PASS;

        boolean matched = false;
        if (!entityData.get(DATA_FOOD_DONE)
                && ItemStack.isSameItemSameComponents(held, entityData.get(DATA_FOOD))) {
            entityData.set(DATA_FOOD_DONE, true);
            matched = true;
        } else if (!entityData.get(DATA_DRINK_DONE)
                && ItemStack.isSameItemSameComponents(held, entityData.get(DATA_DRINK))) {
            entityData.set(DATA_DRINK_DONE, true);
            matched = true;
        }

        if (!player.getAbilities().instabuild) held.shrink(1);

        if (!matched) {
            becomeAngry(serverLevel);
            return InteractionResult.CONSUME;
        }

        serverLevel.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.7F, 1.2F);

        if (entityData.get(DATA_FOOD_DONE) && entityData.get(DATA_DRINK_DONE)) {
            beginHappyReaction();
        }
        return InteractionResult.CONSUME;
    }

    private void beginHappyReaction() {
        setMood(CustomerMood.HAPPY);
        entityData.set(DATA_ORDER_FAILED, false);
        entityData.set(DATA_STATE, REACTING);
        stateTimer = RESULT_DISPLAY_TICKS;
    }

    private void becomeAngry(ServerLevel level) {
        setMood(CustomerMood.ANGRY);
        entityData.set(DATA_ORDER_FAILED, true);
        entityData.set(DATA_STATE, REACTING);
        stateTimer = RESULT_DISPLAY_TICKS;
        angryRewardClaimed = false;
        level.sendParticles(
                ParticleTypes.ANGRY_VILLAGER,
                getX(), getY() + 1.5D, getZ(),
                8, 0.25D, 0.35D, 0.25D, 0.02D
        );
        level.playSound(null, blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
    }

    public void beginLeaving() {
        if (!(level() instanceof ServerLevel serverLevel)) return;

        BlockPos heartPos = getRestaurantHeart();
        RestaurantHeartBlockEntity heart = heartPos == null
                ? null
                : RestaurantManager.get(serverLevel, heartPos).orElse(null);

        standUpFromChair(serverLevel);
        entityData.set(DATA_STATE, LEAVING);
        leavingTicks = 0;

        if (heart != null) {
            setLeavePath(serverLevel, heart);
            heart.syncHudNow(serverLevel);
        } else if (getMood() == CustomerMood.NEUTRAL) {
            detachFromRestaurant(serverLevel);
        } else {
            discard();
        }
    }

    private void standUpFromChair(ServerLevel level) {
        BlockPos chair = getTargetChair();
        Direction exitDirection = chairApproachDirection;
        if (exitDirection == null && chair != null) {
            exitDirection = getChairFrontDirection(chair).getClockWise();
        }
        if (exitDirection == null) exitDirection = Direction.SOUTH;
        cleanupSeat(level);

        if (chair != null && level.getBlockState(chair).is(ModContent.CHAIR.get())) {
            double x = chair.getX() + 0.5D + exitDirection.getStepX() * 0.92D;
            double z = chair.getZ() + 0.5D + exitDirection.getStepZ() * 0.92D;
            moveTo(x, chair.getY(), z, exitDirection.toYRot(), 0.0F);
        }
    }

    private void setLeavePath(ServerLevel level, RestaurantHeartBlockEntity heart) {
        Vec3 exitTarget = heart.findNearestExitTarget(getX(), getZ(), 7.0D);
        double targetX = exitTarget.x;
        double targetZ = exitTarget.z;
        int targetY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.util.Mth.floor(targetX),
                net.minecraft.util.Mth.floor(targetZ)
        );

        double speed = switch (getMood()) {
            case HAPPY -> 0.75D;
            case ANGRY -> 0.70D;
            case NEUTRAL -> 0.55D;
        };
        getNavigation().moveTo(targetX, targetY, targetZ, speed);
    }

    private boolean validChair(ServerLevel level) {
        BlockPos chair = getTargetChair();
        return chair != null && level.getBlockState(chair).is(ModContent.CHAIR.get());
    }

    private void trackStuckProgress() {
        if (tickCount % 20 != 0) return;

        double moved = (getX() - lastProgressX) * (getX() - lastProgressX)
                + (getZ() - lastProgressZ) * (getZ() - lastProgressZ);
        if (moved < 0.04D) stuckTicks += 20;
        else stuckTicks = 0;

        lastProgressX = getX();
        lastProgressZ = getZ();
    }

    private void cleanupSeat(ServerLevel level) {
        Entity vehicle = getVehicle();
        if (isPassenger()) stopRiding();
        if (vehicle instanceof ArmorStand && vehicle.isInvisible()) {
            vehicle.discard();
        }
        if (seatUuid != null) {
            Entity seat = level.getEntity(seatUuid);
            if (seat != null) seat.discard();
            seatUuid = null;
        }
    }

    @Override
    public void kill() {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel) {
            cleanupSeat(serverLevel);
        }

        // LivingEntity#kill routes through hurt(genericKill), while customers deliberately
        // reject normal damage. Remove directly so /kill remains the one true kill method.
        remove(Entity.RemovalReason.KILLED);
        gameEvent(GameEvent.ENTITY_DIE);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        ServerLevel serverLevel = level() instanceof ServerLevel value ? value : null;
        RestaurantHeartBlockEntity heart = null;
        if (serverLevel != null) {
            BlockPos heartPos = getRestaurantHeart();
            heart = heartPos == null ? null : RestaurantManager.get(serverLevel, heartPos).orElse(null);
            if (seatUuid != null || isPassenger()) {
                cleanupSeat(serverLevel);
            }
        }

        super.remove(reason);
        if (serverLevel != null && heart != null) {
            heart.syncHudNow(serverLevel);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Regular damage never kills a customer. /kill still works because the command calls Entity#kill directly.
        if (!level().isClientSide
                && getMood() == CustomerMood.ANGRY
                && getCustomerState() == LEAVING
                && !angryRewardClaimed
                && source.getEntity() instanceof Player player
                && level() instanceof ServerLevel serverLevel) {
            BlockPos heartPos = getRestaurantHeart();
            RestaurantHeartBlockEntity heart = heartPos == null
                    ? null
                    : RestaurantManager.get(serverLevel, heartPos).orElse(null);

            if (heart != null && heart.isMember(player.getUUID())) {
                int recovered = Math.max(1, orderPrice / 2);
                heart.addMoney(recovered);
                angryRewardClaimed = true;
                serverLevel.sendParticles(
                        ModParticles.COIN.get(),
                        getX(), getY() + 1.0D, getZ(),
                        14, 0.30D, 0.35D, 0.30D, 0.12D
                );
                serverLevel.playSound(null, blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.25F);
                player.displayClientMessage(Component.translatable("message.orderup.angry_recovery", recovered), true);
            }
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    public boolean belongsTo(BlockPos heartPos) {
        return heartPos != null && heartPos.equals(getRestaurantHeart());
    }

    public void onRestaurantRemoved() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        if (getMood() == CustomerMood.NEUTRAL) {
            detachFromRestaurant(serverLevel);
        } else {
            cleanupSeat(serverLevel);
            discard();
        }
    }

    public boolean isLeaving() {
        return getCustomerState() == LEAVING;
    }

    public int getCustomerState() {
        return entityData.get(DATA_STATE);
    }

    public CustomerMood getMood() {
        return CustomerMood.byId(entityData.get(DATA_MOOD));
    }

    public void setMood(CustomerMood mood) {
        entityData.set(DATA_MOOD, (mood == null ? CustomerMood.NEUTRAL : mood).id());
    }

    public BlockPos getRestaurantHeart() {
        return entityData.get(DATA_HEART).orElse(null);
    }

    public BlockPos getTargetChair() {
        return entityData.get(DATA_CHAIR).orElse(null);
    }

    public ItemStack getOrderedFood() {
        return entityData.get(DATA_FOOD);
    }

    public ItemStack getOrderedDrink() {
        return entityData.get(DATA_DRINK);
    }

    public boolean isFoodDelivered() {
        return entityData.get(DATA_FOOD_DONE);
    }

    public boolean isDrinkDelivered() {
        return entityData.get(DATA_DRINK_DONE);
    }

    public boolean isOrderFailed() {
        return entityData.get(DATA_ORDER_FAILED);
    }

    @Override
    public VillagerData getVillagerData() {
        return villagerData;
    }

    @Override
    public void setVillagerData(VillagerData data) {
        villagerData = data;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        BlockPos heart = getRestaurantHeart();
        BlockPos chair = getTargetChair();
        if (heart != null) tag.putLong("RestaurantHeart", heart.asLong());
        if (chair != null) tag.putLong("TargetChair", chair.asLong());
        if (menuBoardPos != null) tag.putLong("MenuBoard", menuBoardPos.asLong());

        tag.putInt("CustomerState", getCustomerState());
        tag.putString("mood", getMood().serializedName());
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("LeavingTicks", leavingTicks);
        tag.putInt("OrderPrice", orderPrice);
        tag.putBoolean("OrderFailed", isOrderFailed());
        tag.putBoolean("AngryRewardClaimed", angryRewardClaimed);
        saveItemId(tag, "Food", getOrderedFood());
        saveItemId(tag, "Drink", getOrderedDrink());
        tag.putBoolean("FoodDone", isFoodDelivered());
        tag.putBoolean("DrinkDone", isDrinkDelivered());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        entityData.set(
                DATA_HEART,
                tag.contains("RestaurantHeart")
                        ? Optional.of(BlockPos.of(tag.getLong("RestaurantHeart")))
                        : Optional.empty()
        );
        entityData.set(
                DATA_CHAIR,
                tag.contains("TargetChair")
                        ? Optional.of(BlockPos.of(tag.getLong("TargetChair")))
                        : Optional.empty()
        );
        menuBoardPos = tag.contains("MenuBoard") ? BlockPos.of(tag.getLong("MenuBoard")) : null;

        int savedState = tag.getInt("CustomerState");
        boolean hasMood = tag.contains("mood") || tag.contains("Mood");
        String moodName = tag.contains("mood") ? tag.getString("mood") : tag.getString("Mood");
        CustomerMood savedMood = hasMood ? CustomerMood.byName(moodName) : CustomerMood.NEUTRAL;

        // Backward compatibility with the old ANGRY state value.
        if (!hasMood && savedState == LEGACY_ANGRY_STATE) {
            savedState = LEAVING;
            savedMood = CustomerMood.ANGRY;
        }

        if (!isValidState(savedState)) savedState = WALKING;
        entityData.set(DATA_STATE, savedState);
        setMood(savedMood);

        stateTimer = tag.getInt("StateTimer");
        leavingTicks = Math.max(0, tag.getInt("LeavingTicks"));
        chairApproachDirection = null;
        orderPrice = tag.getInt("OrderPrice");
        entityData.set(DATA_FOOD, loadItemId(tag, "Food"));
        entityData.set(DATA_DRINK, loadItemId(tag, "Drink"));
        entityData.set(DATA_FOOD_DONE, tag.getBoolean("FoodDone"));
        entityData.set(DATA_DRINK_DONE, tag.getBoolean("DrinkDone"));
        entityData.set(DATA_ORDER_FAILED, tag.getBoolean("OrderFailed"));
        angryRewardClaimed = tag.getBoolean("AngryRewardClaimed");
    }

    private static boolean isValidState(int state) {
        return state == WALKING || state == THINKING || state == WAITING || state == LEAVING || state == REACTING;
    }

    private static void saveItemId(CompoundTag tag, String key, ItemStack stack) {
        if (!stack.isEmpty()) {
            tag.putString(key, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
    }

    private static ItemStack loadItemId(CompoundTag tag, String key) {
        if (!tag.contains(key)) return ItemStack.EMPTY;

        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) return ItemStack.EMPTY;

        Item item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    public enum CustomerMood {
        NEUTRAL(0, "neutral"),
        HAPPY(1, "happy"),
        ANGRY(2, "angry");

        private final int id;
        private final String serializedName;

        CustomerMood(int id, String serializedName) {
            this.id = id;
            this.serializedName = serializedName;
        }

        public int id() {
            return id;
        }

        public String serializedName() {
            return serializedName;
        }

        public static CustomerMood byId(int id) {
            for (CustomerMood mood : values()) {
                if (mood.id == id) return mood;
            }
            return NEUTRAL;
        }

        public static CustomerMood byName(String name) {
            if (name == null) return NEUTRAL;
            String normalized = name.strip().toLowerCase(Locale.ROOT);
            for (CustomerMood mood : values()) {
                if (mood.serializedName.equals(normalized)) return mood;
            }
            return NEUTRAL;
        }
    }
}
