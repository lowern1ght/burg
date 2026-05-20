package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.inventory.MenuType;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;

public class MenuRegistry {
    // Set by platform init (OuatFabric / OuatForge) before any menu is opened
    public static MenuType<VillageChestMenu> VILLAGE_CHEST;
}
