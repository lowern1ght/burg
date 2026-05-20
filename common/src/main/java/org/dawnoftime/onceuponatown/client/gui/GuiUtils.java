package org.dawnoftime.onceuponatown.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class GuiUtils {
    public static void drawCenteredString(GuiGraphics graphics, Font font, Component component,
                                          int xPos, int yPos, int backgroundWidth, int color) {
        graphics.drawString(font, component, xPos + (backgroundWidth - font.width(component)) / 2, yPos, color, false);
    }
}
