package org.dawnoftime.onceuponatown.registry;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.dawnoftime.onceuponatown.menu.*;

import java.util.function.Supplier;

public abstract class MenuRegistry {
    public static MenuRegistry REGISTRY;

    public final Supplier<MenuType<TradeMenu>> TRADE_MENU = register("trade_menu", TradeMenu::new);
    public final Supplier<MenuType<QuestsMenu>> QUESTS_MENU = register("quests_menu", QuestsMenu::new);
    public final Supplier<MenuType<BuildingsMenu>> BUILDINGS_MENU = register("buildings_menu", BuildingsMenu::new);
    public final Supplier<MenuType<ProgressionMenu>> PROGRESSION_MENU = register("progression_menu", ProgressionMenu::new);
    public final Supplier<MenuType<ProjectsMenu>> PROJECT_MENU = register("project_menu", ProjectsMenu::new);
    public final Supplier<MenuType<DiplomacyMenu>> DIPLOMACY_MENU = register("diplomacy_menu", DiplomacyMenu::new);

    public abstract <T extends AbstractContainerMenu> Supplier<MenuType<T>> register(final String name, MenuTypeFactory<T> factory);

    @FunctionalInterface
    public interface MenuTypeFactory<T extends AbstractContainerMenu> {
        T create(int windowId, Inventory playerInventory, FriendlyByteBuf additionalData);
    }
}