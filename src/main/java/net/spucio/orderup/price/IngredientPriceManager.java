package net.spucio.orderup.price;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLPaths;
import net.spucio.orderup.OrderUp;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;

public final class IngredientPriceManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Integer> VALUES = new LinkedHashMap<>();
    private static int unknownIngredientPrice = 1;

    private IngredientPriceManager() {}

    public static void load() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(OrderUp.MOD_ID);
        Path file = dir.resolve("ingredient_prices.json");
        try {
            Files.createDirectories(dir);
            if (Files.notExists(file)) writeDefault(file);
            try (Reader reader = Files.newBufferedReader(file)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                VALUES.clear();
                if (root != null && root.has("unknown_ingredient_price")) {
                    unknownIngredientPrice = Math.max(0, root.get("unknown_ingredient_price").getAsInt());
                }
                if (root != null && root.has("values") && root.get("values").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("values").entrySet()) {
                        VALUES.put(entry.getKey(), Math.max(0, entry.getValue().getAsInt()));
                    }
                }
            }
        } catch (Exception exception) {
            System.err.println("[Order Up!] Failed to load ingredient_prices.json: " + exception.getMessage());
        }
    }

    public static OptionalInt configuredPrice(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Integer direct = VALUES.get(itemId.toString());
        if (direct != null) return OptionalInt.of(direct);

        for (Map.Entry<String, Integer> entry : VALUES.entrySet()) {
            if (!entry.getKey().startsWith("#")) continue;
            ResourceLocation tagId = ResourceLocation.tryParse(entry.getKey().substring(1));
            if (tagId == null) continue;
            TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
            if (stack.is(tag)) return OptionalInt.of(entry.getValue());
        }
        return OptionalInt.empty();
    }

    public static int unknownIngredientPrice() {
        return unknownIngredientPrice;
    }

    private static void writeDefault(Path file) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Use item ids or #tag ids. Values are ingredient prices in restaurant dollars.");
        root.addProperty("unknown_ingredient_price", 1);
        JsonObject values = new JsonObject();
        values.addProperty("minecraft:beef", 5);
        values.addProperty("minecraft:porkchop", 5);
        values.addProperty("minecraft:chicken", 4);
        values.addProperty("minecraft:mutton", 4);
        values.addProperty("minecraft:rabbit", 5);
        values.addProperty("minecraft:wheat", 1);
        values.addProperty("minecraft:sugar", 1);
        values.addProperty("minecraft:egg", 2);
        values.addProperty("minecraft:milk_bucket", 3);
        values.addProperty("minecraft:cocoa_beans", 2);
        values.addProperty("#orderup:vegetables", 2);
        root.add("values", values);
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(root, writer);
        }
    }
}
