package pl.losspucios.orderup.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import pl.losspucios.orderup.ModContent;
import pl.losspucios.orderup.blockentity.MenuBoardBlockEntity;
import pl.losspucios.orderup.blockentity.RestaurantHeartBlockEntity;
import pl.losspucios.orderup.restaurant.RestaurantManager;

import java.util.Optional;
import java.util.UUID;

public class CustomerEntity extends PathfinderMob implements VillagerDataHolder {
    public static final int WALKING = 0;
    public static final int THINKING = 1;
    public static final int WAITING = 2;
    public static final int LEAVING = 3;
    public static final int ANGRY = 4;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_HEART = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<Optional<BlockPos>> DATA_CHAIR = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS);
    private static final EntityDataAccessor<ItemStack> DATA_FOOD = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<ItemStack> DATA_DRINK = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> DATA_FOOD_DONE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_DRINK_DONE = SynchedEntityData.defineId(CustomerEntity.class, EntityDataSerializers.BOOLEAN);

    private BlockPos menuBoardPos;
    private int orderPrice;
    private int stateTimer;
    private int stuckTicks;
    private double lastProgressX;
    private double lastProgressZ;
    private UUID seatUuid;
    private VillagerData villagerData = new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1);

    public CustomerEntity(EntityType<? extends CustomerEntity> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.42D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    @Override
    protected void registerGoals() {
        // Order Up controls navigation directly; no wandering or combat goals are installed.
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STATE, WALKING);
        builder.define(DATA_HEART, Optional.empty());
        builder.define(DATA_CHAIR, Optional.empty());
        builder.define(DATA_FOOD, ItemStack.EMPTY);
        builder.define(DATA_DRINK, ItemStack.EMPTY);
        builder.define(DATA_FOOD_DONE, false);
        builder.define(DATA_DRINK_DONE, false);
    }

    public void setRestaurantContext(BlockPos heartPos, BlockPos chairPos, BlockPos menuPos) {
        entityData.set(DATA_HEART, Optional.of(heartPos.immutable()));
        entityData.set(DATA_CHAIR, Optional.of(chairPos.immutable()));
        menuBoardPos = menuPos == null ? null : menuPos.immutable();
        entityData.set(DATA_STATE, WALKING);
    }

    public boolean beginWalkingToChair() {
        BlockPos chair = getTargetChair();
        if (chair == null) return false;

        Path bestPath = null;
        double bestDistance = Double.MAX_VALUE;

        // A chair is a solid block, so pathfinding to the chair block itself often fails.
        // Instead, pathfind to the closest safe horizontal block next to it.
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos approachPos = chair.relative(direction);
            BlockPos above = approachPos.above();

            if (!level().getBlockState(approachPos).getCollisionShape(level(), approachPos).isEmpty()) continue;
            if (!level().getBlockState(above).getCollisionShape(level(), above).isEmpty()) continue;

            Path path = getNavigation().createPath(approachPos, 0);
            if (path == null || !path.canReach()) continue;

            double distance = approachPos.distSqr(blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                bestPath = path;
            }
        }

        if (bestPath == null) return false;

        lastProgressX = getX();
        lastProgressZ = getZ();
        stuckTicks = 0;
        getNavigation().moveTo(bestPath, 0.55D);
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        ServerLevel level = (ServerLevel) level();
        BlockPos heartPos = getRestaurantHeart();

        // A customer spawned manually with /summon has no restaurant context.
        // Keep it alive for testing instead of deleting it on its first server tick.
        if (heartPos == null) {
            return;
        }

        RestaurantHeartBlockEntity heart = RestaurantManager.get(level, heartPos).orElse(null);
        if (heart == null) {
            discard();
            return;
        }

        int state = getCustomerState();
        if (state == WALKING) tickWalking(level, heart);
        else if (state == THINKING) tickThinking(level, heart);
        else if (state == WAITING) tickWaiting(level, heart);
        else if (state == LEAVING || state == ANGRY) tickLeaving(level, heart);
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

        double dx = getX() - (chair.getX() + 0.5D);
        double dz = getZ() - (chair.getZ() + 0.5D);
        if (dx * dx + dz * dz < 1.25D) {
            sitDown(level, chair);
            return;
        }

        if (tickCount % 40 == 0 && getNavigation().isDone() && !beginWalkingToChair()) {
            beginLeaving();
            return;
        }
        trackStuckProgress();
        if (stuckTicks > 120) discard();
    }

    private void tickThinking(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (!validChair(level)) {
            beginLeaving();
            return;
        }
        if (--stateTimer <= 0) chooseOrder(level, heart);
    }

    private void tickWaiting(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (!validChair(level)) beginLeaving();
    }

    private void tickLeaving(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (!heart.contains(blockPosition()) && distanceToSqr(
                heart.getBlockPos().getX() + 0.5D,
                getY(),
                heart.getBlockPos().getZ() + 0.5D
        ) > (heart.getRadius() + 6.0D) * (heart.getRadius() + 6.0D)) {
            cleanupSeat(level);
            discard();
            return;
        }

        if (tickCount % 40 == 0 && getNavigation().isDone()) setLeavePath(heart);
        trackStuckProgress();
        if (stuckTicks > 120) {
            cleanupSeat(level);
            discard();
        }
    }

    private void sitDown(ServerLevel level, BlockPos chair) {
        getNavigation().stop();
        ArmorStand seat = EntityType.ARMOR_STAND.create(level);
        if (seat != null) {
            seat.setInvisible(true);
            seat.setInvulnerable(true);
            seat.setNoGravity(true);
            seat.setPos(chair.getX() + 0.5D, chair.getY() - 0.55D, chair.getZ() + 0.5D);
            level.addFreshEntity(seat);
            seatUuid = seat.getUUID();
            startRiding(seat, true);
        } else {
            setPos(chair.getX() + 0.5D, chair.getY(), chair.getZ() + 0.5D);
        }
        entityData.set(DATA_STATE, THINKING);
        stateTimer = 60 + random.nextInt(81);
        stuckTicks = 0;
    }

    private void chooseOrder(ServerLevel level, RestaurantHeartBlockEntity heart) {
        if (menuBoardPos == null || !(level.getBlockEntity(menuBoardPos) instanceof MenuBoardBlockEntity menu) || !menu.isFull()) {
            beginLeaving();
            return;
        }

        int foodSlot = random.nextInt(MenuBoardBlockEntity.FOOD_SLOTS);
        ItemStack food = menu.getGhostItem(foodSlot);
        ItemStack drink = random.nextFloat() < 0.40F
                ? menu.getGhostItem(MenuBoardBlockEntity.FOOD_SLOTS + random.nextInt(MenuBoardBlockEntity.DRINK_SLOTS))
                : ItemStack.EMPTY;

        entityData.set(DATA_FOOD, food.copyWithCount(1));
        entityData.set(DATA_DRINK, drink.isEmpty() ? ItemStack.EMPTY : drink.copyWithCount(1));
        entityData.set(DATA_FOOD_DONE, false);
        entityData.set(DATA_DRINK_DONE, drink.isEmpty());
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
        RestaurantHeartBlockEntity heart = heartPos == null ? null : RestaurantManager.get(serverLevel, heartPos).orElse(null);
        if (heart == null || !heart.isMember(player.getUUID())) return InteractionResult.FAIL;

        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return InteractionResult.PASS;

        boolean matched = false;
        if (!entityData.get(DATA_FOOD_DONE) && ItemStack.isSameItemSameComponents(held, entityData.get(DATA_FOOD))) {
            entityData.set(DATA_FOOD_DONE, true);
            matched = true;
        } else if (!entityData.get(DATA_DRINK_DONE) && ItemStack.isSameItemSameComponents(held, entityData.get(DATA_DRINK))) {
            entityData.set(DATA_DRINK_DONE, true);
            matched = true;
        }

        if (!matched) {
            becomeAngry(serverLevel, heart);
            return InteractionResult.CONSUME;
        }

        if (!player.getAbilities().instabuild) held.shrink(1);
        serverLevel.playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.7F, 1.2F);

        if (entityData.get(DATA_FOOD_DONE) && entityData.get(DATA_DRINK_DONE)) {
            completeHappyOrder(serverLevel, heart);
        }
        return InteractionResult.CONSUME;
    }

    private void completeHappyOrder(ServerLevel level, RestaurantHeartBlockEntity heart) {
        heart.addMoney(orderPrice);
        heart.addRestaurantXp(10 + Math.max(1, orderPrice / 2));
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, getX(), getY() + 1.4D, getZ(), 10, 0.35D, 0.45D, 0.35D, 0.02D);
        beginLeaving();
    }

    private void becomeAngry(ServerLevel level, RestaurantHeartBlockEntity heart) {
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, getX(), getY() + 1.5D, getZ(), 8, 0.25D, 0.35D, 0.25D, 0.02D);
        cleanupSeat(level);
        entityData.set(DATA_STATE, ANGRY);
        setLeavePath(heart);
    }

    public void beginLeaving() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        BlockPos heartPos = getRestaurantHeart();
        RestaurantHeartBlockEntity heart = heartPos == null ? null : RestaurantManager.get(serverLevel, heartPos).orElse(null);
        cleanupSeat(serverLevel);
        if (getCustomerState() != ANGRY) entityData.set(DATA_STATE, LEAVING);
        if (heart != null) setLeavePath(heart);
    }

    private void setLeavePath(RestaurantHeartBlockEntity heart) {
        double dx = getX() - (heart.getBlockPos().getX() + 0.5D);
        double dz = getZ() - (heart.getBlockPos().getZ() + 0.5D);
        if (Math.abs(dx) + Math.abs(dz) < 0.1D) dx = random.nextBoolean() ? 1.0D : -1.0D;
        double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
        double distance = heart.getRadius() + 12.0D;
        double targetX = heart.getBlockPos().getX() + 0.5D + dx / length * distance;
        double targetZ = heart.getBlockPos().getZ() + 0.5D + dz / length * distance;
        getNavigation().moveTo(targetX, getY(), targetZ, getCustomerState() == ANGRY ? 0.72D : 0.55D);
    }

    private boolean validChair(ServerLevel level) {
        BlockPos chair = getTargetChair();
        return chair != null && level.getBlockState(chair).is(ModContent.CHAIR.get());
    }

    private void trackStuckProgress() {
        if (tickCount % 20 != 0) return;
        double moved = (getX() - lastProgressX) * (getX() - lastProgressX) + (getZ() - lastProgressZ) * (getZ() - lastProgressZ);
        if (moved < 0.04D) stuckTicks += 20;
        else stuckTicks = 0;
        lastProgressX = getX();
        lastProgressZ = getZ();
    }

    private void cleanupSeat(ServerLevel level) {
        if (isPassenger()) stopRiding();
        if (seatUuid != null) {
            Entity seat = level.getEntity(seatUuid);
            if (seat != null) seat.discard();
            seatUuid = null;
        }
    }


    @Override
    public void remove(Entity.RemovalReason reason) {
        if (!level().isClientSide && level() instanceof ServerLevel serverLevel && seatUuid != null) {
            cleanupSeat(serverLevel);
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getCustomerState() != ANGRY) return false;
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && getCustomerState() == ANGRY && source.getEntity() instanceof Player player) {
            BlockPos heartPos = getRestaurantHeart();
            if (heartPos != null && level() instanceof ServerLevel serverLevel) {
                RestaurantManager.get(serverLevel, heartPos).ifPresent(heart -> {
                    if (heart.isMember(player.getUUID())) heart.addMoney(Math.max(1, orderPrice / 2));
                });
            }
        }
        if (level() instanceof ServerLevel serverLevel) cleanupSeat(serverLevel);
        super.die(source);
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

    public boolean isLeaving() {
        return getCustomerState() == LEAVING || getCustomerState() == ANGRY;
    }

    public int getCustomerState() {
        return entityData.get(DATA_STATE);
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
        tag.putInt("StateTimer", stateTimer);
        tag.putInt("OrderPrice", orderPrice);
        saveItemId(tag, "Food", getOrderedFood());
        saveItemId(tag, "Drink", getOrderedDrink());
        tag.putBoolean("FoodDone", isFoodDelivered());
        tag.putBoolean("DrinkDone", isDrinkDelivered());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_HEART, tag.contains("RestaurantHeart") ? Optional.of(BlockPos.of(tag.getLong("RestaurantHeart"))) : Optional.empty());
        entityData.set(DATA_CHAIR, tag.contains("TargetChair") ? Optional.of(BlockPos.of(tag.getLong("TargetChair"))) : Optional.empty());
        menuBoardPos = tag.contains("MenuBoard") ? BlockPos.of(tag.getLong("MenuBoard")) : null;
        entityData.set(DATA_STATE, tag.getInt("CustomerState"));
        stateTimer = tag.getInt("StateTimer");
        orderPrice = tag.getInt("OrderPrice");
        entityData.set(DATA_FOOD, loadItemId(tag, "Food"));
        entityData.set(DATA_DRINK, loadItemId(tag, "Drink"));
        entityData.set(DATA_FOOD_DONE, tag.getBoolean("FoodDone"));
        entityData.set(DATA_DRINK_DONE, tag.getBoolean("DrinkDone"));
    }

    private static void saveItemId(CompoundTag tag, String key, ItemStack stack) {
        if (!stack.isEmpty()) tag.putString(key, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
    }

    private static ItemStack loadItemId(CompoundTag tag, String key) {
        if (!tag.contains(key)) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }
}
