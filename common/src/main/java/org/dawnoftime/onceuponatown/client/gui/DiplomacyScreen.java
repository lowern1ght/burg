package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.menu.DiplomacyMenu;

public class DiplomacyScreen extends NpcBaseScreen<DiplomacyMenu> {
    public DiplomacyScreen(DiplomacyMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected Tab getTab() {
        return Tab.DIPLOMACY;
    }
}
