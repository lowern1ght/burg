package org.dawnoftime.onceuponatown.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.dawnoftime.onceuponatown.menu.TradeMenu;

import java.util.function.Supplier;

public abstract class MenuRegistry {
    public static MenuRegistry REGISTRY;

    public final Supplier<MenuType<TradeMenu>> TRADE_MENU = register("trade_menu", TradeMenu::new);

    public abstract <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(final String name, MenuTypeFactory<T> factory);

    @FunctionalInterface
    public interface MenuTypeFactory<T extends AbstractContainerMenu> {
        T create(int windowId, Inventory playerInventory, FriendlyByteBuf additionalData);
    }
}