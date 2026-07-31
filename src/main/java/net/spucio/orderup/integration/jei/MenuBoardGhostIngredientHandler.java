package net.spucio.orderup.integration.jei;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.spucio.orderup.client.MenuBoardScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MenuBoardGhostIngredientHandler implements IGhostIngredientHandler<MenuBoardScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(
            MenuBoardScreen screen,
            ITypedIngredient<I> ingredient,
            boolean doStart
    ) {
        Optional<ItemStack> optionalStack = ingredient.getItemStack();
        if (optionalStack.isEmpty()) return List.of();

        ItemStack stack = optionalStack.get().copyWithCount(1);
        List<Target<I>> targets = new ArrayList<>();
        for (int slot = 0; slot < screen.getMenuSlotCountForJei(); slot++) {
            if (!screen.canAcceptJeiIngredient(stack, slot)) continue;

            int targetSlot = slot;
            int x = screen.getMenuSlotXForJei(slot);
            int y = screen.getMenuSlotYForJei(slot);
            int size = screen.getMenuSlotSizeForJei();
            Rect2i area = new Rect2i(x, y, size, size);

            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ignoredIngredient) {
                    screen.acceptJeiIngredient(stack, targetSlot);
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
