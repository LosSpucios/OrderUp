package pl.losspucios.orderup.price;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;

public final class PriceCalculator {
    private static final RecipeType<?>[] SUPPORTED_TYPES = {
            RecipeType.CRAFTING,
            RecipeType.SMELTING,
            RecipeType.SMOKING,
            RecipeType.CAMPFIRE_COOKING
    };

    private PriceCalculator() {}

    public static int calculate(ServerLevel level, ItemStack result) {
        if (result.isEmpty()) return 0;
        return Math.max(1, calculateInternal(level, result, new HashSet<>(), 0));
    }

    private static int calculateInternal(ServerLevel level, ItemStack stack, Set<Item> visiting, int depth) {
        OptionalInt configured = IngredientPriceManager.configuredPrice(stack);
        if (configured.isPresent()) return configured.getAsInt() * Math.max(1, stack.getCount());
        if (depth > 8 || !visiting.add(stack.getItem())) return IngredientPriceManager.unknownIngredientPrice();

        int best = Integer.MAX_VALUE;
        for (RecipeType<?> type : SUPPORTED_TYPES) {
            best = Math.min(best, cheapestRecipe(level, type, stack.getItem(), visiting, depth));
        }
        visiting.remove(stack.getItem());

        if (best == Integer.MAX_VALUE) {
            return IngredientPriceManager.unknownIngredientPrice() * Math.max(1, stack.getCount());
        }
        return Math.max(1, best);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int cheapestRecipe(ServerLevel level, RecipeType type, Item wanted, Set<Item> visiting, int depth) {
        int best = Integer.MAX_VALUE;
        for (Object object : level.getRecipeManager().getAllRecipesFor(type)) {
            RecipeHolder holder = (RecipeHolder) object;
            Recipe recipe = holder.value();
            ItemStack result = recipe.getResultItem(level.registryAccess());
            if (!result.is(wanted)) continue;

            int total = 0;
            boolean valid = true;
            for (Object ingredientObject : recipe.getIngredients()) {
                Ingredient ingredient = (Ingredient) ingredientObject;
                if (ingredient.isEmpty()) continue;
                int optionBest = Integer.MAX_VALUE;
                for (ItemStack option : ingredient.getItems()) {
                    optionBest = Math.min(optionBest, calculateInternal(level, option.copyWithCount(1), new HashSet<>(visiting), depth + 1));
                }
                if (optionBest == Integer.MAX_VALUE) {
                    valid = false;
                    break;
                }
                total += optionBest;
            }
            if (valid) {
                int outputCount = Math.max(1, result.getCount());
                best = Math.min(best, Math.max(1, (int) Math.ceil(total / (double) outputCount)));
            }
        }
        return best;
    }
}
