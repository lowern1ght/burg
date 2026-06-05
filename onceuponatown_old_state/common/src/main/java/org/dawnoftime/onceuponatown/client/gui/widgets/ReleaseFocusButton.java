package org.dawnoftime.onceuponatown.client.gui.widgets;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class ReleaseFocusButton extends Button {
    public ReleaseFocusButton(int x, int y, int width, int height, Component message, OnPress onPress, Tooltip tooltip) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        setTooltip(tooltip);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        this.setFocused(false);
        super.onRelease(mouseX, mouseY);
    }
}
