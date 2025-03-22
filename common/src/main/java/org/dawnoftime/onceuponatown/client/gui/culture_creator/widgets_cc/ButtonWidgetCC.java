package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class ButtonWidgetCC extends WidgetCC {

    private final LeftAlignTextButton button;

    /**
     * Adds a row to the screen that contains only a button.
     *
     * @param posX    X position of the GUI.
     * @param onPress OnPress effect of the button.
     */
    public ButtonWidgetCC(int posX, Component text, WidgetAction onPress) {
        this.button = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT, text, btn -> onPress.execute(this));
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{button};
    }
}
