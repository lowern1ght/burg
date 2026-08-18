package org.lowern1ght.burg.client.gui.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

public class ClientItemAndTitleTooltip implements ClientTooltipComponent {
    private final ItemAndTitleTooltip data;

    public ClientItemAndTitleTooltip(ItemAndTitleTooltip data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public int getWidth(Font font) {
        return 20;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.renderItem(data.stack(), x, y + 2);
    }
}
