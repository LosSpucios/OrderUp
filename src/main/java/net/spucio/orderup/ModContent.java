package net.spucio.orderup;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.spucio.orderup.block.ChairBlock;
import net.spucio.orderup.block.MenuBoardBlock;
import net.spucio.orderup.block.OpenSignBlock;
import net.spucio.orderup.block.RestaurantHeartBlock;
import net.spucio.orderup.block.TableBlock;
import net.spucio.orderup.blockentity.MenuBoardBlockEntity;
import net.spucio.orderup.blockentity.OpenSignBlockEntity;
import net.spucio.orderup.blockentity.RestaurantHeartBlockEntity;
import net.spucio.orderup.entity.CustomerEntity;
import net.spucio.orderup.item.RestaurantRestrictedBlockItem;

public final class ModContent {
    private ModContent() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(OrderUp.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OrderUp.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, OrderUp.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, OrderUp.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OrderUp.MOD_ID);

    public static final DeferredBlock<RestaurantHeartBlock> RESTAURANT_HEART = BLOCKS.register(
            "restaurant_heart",
            () -> new RestaurantHeartBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.DEEPSLATE)
                    .lightLevel(state -> 7)
                    .noOcclusion())
    );

    public static final DeferredBlock<TableBlock> TABLE = BLOCKS.register(
            "restaurant_table",
            () -> new TableBlock(BlockBehaviour.Properties.of().strength(2.0F).sound(SoundType.WOOD).noOcclusion())
    );

    public static final DeferredBlock<ChairBlock> CHAIR = BLOCKS.register(
            "restaurant_chair",
            () -> new ChairBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion())
    );

    public static final DeferredBlock<MenuBoardBlock> MENU_BOARD = BLOCKS.register(
            "menu_board",
            () -> new MenuBoardBlock(BlockBehaviour.Properties.of().strength(1.5F).sound(SoundType.WOOD).noOcclusion())
    );

    public static final DeferredBlock<OpenSignBlock> OPEN_SIGN = BLOCKS.register(
            "open_sign",
            () -> new OpenSignBlock(BlockBehaviour.Properties.of().strength(1.0F).sound(SoundType.WOOD).noOcclusion())
    );

    public static final DeferredItem<BlockItem> RESTAURANT_HEART_ITEM = ITEMS.registerSimpleBlockItem("restaurant_heart", RESTAURANT_HEART);
    public static final DeferredItem<RestaurantRestrictedBlockItem> TABLE_ITEM = ITEMS.register(
            "restaurant_table",
            () -> new RestaurantRestrictedBlockItem(TABLE.get(), new Item.Properties(), RestaurantRestrictedBlockItem.Kind.TABLE)
    );
    public static final DeferredItem<RestaurantRestrictedBlockItem> CHAIR_ITEM = ITEMS.register(
            "restaurant_chair",
            () -> new RestaurantRestrictedBlockItem(CHAIR.get(), new Item.Properties(), RestaurantRestrictedBlockItem.Kind.CHAIR)
    );
    public static final DeferredItem<RestaurantRestrictedBlockItem> MENU_BOARD_ITEM = ITEMS.register(
            "menu_board",
            () -> new RestaurantRestrictedBlockItem(MENU_BOARD.get(), new Item.Properties(), RestaurantRestrictedBlockItem.Kind.MENU)
    );
    public static final DeferredItem<RestaurantRestrictedBlockItem> OPEN_SIGN_ITEM = ITEMS.register(
            "open_sign",
            () -> new RestaurantRestrictedBlockItem(OPEN_SIGN.get(), new Item.Properties(), RestaurantRestrictedBlockItem.Kind.OPEN_SIGN)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RestaurantHeartBlockEntity>> RESTAURANT_HEART_BE = BLOCK_ENTITIES.register(
            "restaurant_heart",
            () -> BlockEntityType.Builder.of(RestaurantHeartBlockEntity::new, RESTAURANT_HEART.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MenuBoardBlockEntity>> MENU_BOARD_BE = BLOCK_ENTITIES.register(
            "menu_board",
            () -> BlockEntityType.Builder.of(MenuBoardBlockEntity::new, MENU_BOARD.get()).build(null)
    );

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OpenSignBlockEntity>> OPEN_SIGN_BE = BLOCK_ENTITIES.register(
            "open_sign",
            () -> BlockEntityType.Builder.of(OpenSignBlockEntity::new, OPEN_SIGN.get()).build(null)
    );

    public static final DeferredHolder<EntityType<?>, EntityType<CustomerEntity>> CUSTOMER = ENTITIES.register(
            "customer",
            () -> EntityType.Builder.<CustomerEntity>of(CustomerEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("orderup:customer")
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ORDER_UP_TAB = TABS.register(
            "order_up",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.orderup"))
                    .icon(() -> RESTAURANT_HEART_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(RESTAURANT_HEART_ITEM.get());
                        output.accept(TABLE_ITEM.get());
                        output.accept(CHAIR_ITEM.get());
                        output.accept(MENU_BOARD_ITEM.get());
                        output.accept(OPEN_SIGN_ITEM.get());
                    })
                    .build()
    );

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        TABS.register(bus);
    }

    public static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(CUSTOMER.get(), CustomerEntity.createAttributes().build());
    }
}
