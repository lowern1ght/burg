package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.menu.BuildingsMenu;

public class BuildingsScreen extends NpcBaseScreen<BuildingsMenu> {
    public BuildingsScreen(BuildingsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected Tab getTab() {
        return Tab.BUILDINGS;
    }
}
