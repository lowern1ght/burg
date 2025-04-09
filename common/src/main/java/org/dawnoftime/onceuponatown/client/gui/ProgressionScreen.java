package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.menu.ProgressionMenu;

public class ProgressionScreen extends NpcBaseScreen<ProgressionMenu> {
    public ProgressionScreen(ProgressionMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected Tab getTab() {
        return Tab.PROGRESSION;
    }
}
