package org.lowern1ght.burg.registry;

import net.minecraft.world.inventory.MenuType;
import org.lowern1ght.burg.screen.TownHubMenu;

public class MenuRegistry {
    // Set by platform init (OuatFabric / OuatForge) before any menu is opened
    public static MenuType<TownHubMenu> TOWN_HUB;
}
