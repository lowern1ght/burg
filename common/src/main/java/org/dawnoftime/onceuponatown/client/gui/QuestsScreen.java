package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.menu.QuestsMenu;

public class QuestsScreen extends NpcBaseScreen<QuestsMenu> {
    private static final int NPC_DOLL_SCALE = 30;
    private static final int NPC_DOLL_X = 250;
    private static final int NPC_DOLL_Y = 80;

    public QuestsScreen(QuestsMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int professionWidth = font.width(Utils.capitalize(menu.getNpc().getNpc().getProfessionId()));
        //graphics.drawString(font, Utils.capitalize(menu.getNpc().getNpc().getProfessionId()), 100,100, 4210752, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderNpcDoll(graphics, mouseX + 2, mouseY + 48, NPC_DOLL_X, NPC_DOLL_Y, NPC_DOLL_SCALE);
        renderInventory(graphics);
    }

    @Override
    protected Tab getTab() {
        return Tab.QUESTS;
    }
}
