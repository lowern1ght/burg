package org.dawnoftime.onceuponatown.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class ScreenUtils {

    public static final int GUI_COLOR_GREY = 4210752;

    public static void drawCenteredString(@NotNull GuiGraphics guiGraphics, Font font, Component component, int xPos, int yPos, int backgroundWidth, int colorInt) {
        guiGraphics.drawString(font, component, xPos + (backgroundWidth - font.width(component)) / 2, yPos, colorInt, false);
    }
}
