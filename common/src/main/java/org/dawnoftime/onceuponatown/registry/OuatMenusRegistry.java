package org.dawnoftime.onceuponatown.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.menu.BuyMenu;
import org.dawnoftime.onceuponatown.menu.SellMenu;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public abstract class OuatMenusRegistry {
    public static OuatMenusRegistry MENU_REGISTRY;

    public final Supplier<MenuType<BuyMenu>> BUY_MENU = register("buy_menu", BuyMenu::new);
    public final Supplier<MenuType<SellMenu>> SELL_MENU = register("sell_menu", SellMenu::new);

    public abstract <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(final String name, MenuTypeFactory<T> factory);

    @FunctionalInterface
    public interface MenuTypeFactory<T extends AbstractContainerMenu> {
        T create(int windowId, Inventory playerInventory, FriendlyByteBuf additionalData);
    }
}