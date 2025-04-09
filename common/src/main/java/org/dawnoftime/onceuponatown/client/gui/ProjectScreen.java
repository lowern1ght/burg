package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.menu.ProjectMenu;

public class ProjectScreen extends NpcBaseScreen<ProjectMenu> {
    public ProjectScreen(ProjectMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected Tab getTab() {
        return Tab.PROJECTS;
    }
}
