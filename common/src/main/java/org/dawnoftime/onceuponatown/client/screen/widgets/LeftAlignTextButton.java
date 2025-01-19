package org.dawnoftime.onceuponatown.client.screen.widgets;


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class LeftAlignTextButton extends Button {

    public LeftAlignTextButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
    }

    public LeftAlignTextButton(int x, int y, int width, int height, Component message) {
        this(x, y, width, height, message, btn -> {});
        this.active = false;
    }

    @Override
    public void renderString(@NotNull GuiGraphics guiGraphics, @NotNull Font font, int color) {
        this.renderScrollingString(guiGraphics, font, 5, color);
    }

    @Override
    protected void renderScrollingString(@NotNull GuiGraphics guiGraphics, @NotNull Font font, int padding, int color) {
        int minX = this.getX() + padding;
        int maxX = this.getX() + this.getWidth() - padding;
        this.renderLeftString(guiGraphics, font, this.getMessage(), minX, this.getY(), maxX, this.getY() + this.getHeight(), color);
    }

    private void renderLeftString(GuiGraphics guiGraphics, Font font, Component text, int minX, int minY, int maxX, int maxY, int color) {
        int textWidth = font.width(text);
        int availableWidth = maxX - minX;
        int centeredYStart = (minY + maxY - 9) / 2 + 1;
        if (textWidth > availableWidth) {
            guiGraphics.enableScissor(minX, minY, maxX, maxY);
            guiGraphics.drawString(font, text, minX, centeredYStart, color);
            guiGraphics.disableScissor();
        } else {
            guiGraphics.drawString(font, text, minX, centeredYStart, color);
        }
    }
}
