package net.spucio.orderup.integration.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.spucio.orderup.OrderUp;
import net.spucio.orderup.client.MenuBoardScreen;

@JeiPlugin
public final class OrderUpJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(OrderUp.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(MenuBoardScreen.class, screen -> new IGuiProperties() {
            @Override
            public Class<? extends Screen> screenClass() {
                return MenuBoardScreen.class;
            }

            @Override
            public int guiLeft() {
                return screen.getGuiLeftForJei();
            }

            @Override
            public int guiTop() {
                return screen.getGuiTopForJei();
            }

            @Override
            public int guiXSize() {
                return screen.getGuiWidthForJei();
            }

            @Override
            public int guiYSize() {
                return screen.getGuiHeightForJei();
            }

            @Override
            public int screenWidth() {
                return screen.getScreenWidthForJei();
            }

            @Override
            public int screenHeight() {
                return screen.getScreenHeightForJei();
            }
        });
        registration.addGhostIngredientHandler(MenuBoardScreen.class, new MenuBoardGhostIngredientHandler());
    }
}
