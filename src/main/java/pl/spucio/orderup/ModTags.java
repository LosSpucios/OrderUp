package pl.spucio.orderup;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModTags {
    private ModTags() {}

    public static final TagKey<Item> DRINKS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(OrderUp.MOD_ID, "drinks")
    );

    public static final TagKey<Item> VEGETABLES = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(OrderUp.MOD_ID, "vegetables")
    );
}
