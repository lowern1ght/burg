package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class AddWidgetCC extends WidgetCC {

    private final IconButton button;

    /**
     * Adds a row to the screen that contains only a button.
     *
     * @param posX    X position of the GUI.
     * @param onPress OnPress effect of the button.
     */
    public AddWidgetCC(int posX, WidgetAction onPress) {
        this.button = new IconButton(
                posX + WIDGET_ZONE_X + (WIDGET_ZONE_WIDTH / 2) - (WIDGET_PLUS_BUTTON_HEIGHT /2),
                (WIDGET_HEIGHT - WIDGET_PLUS_BUTTON_HEIGHT) / 2,
                WIDGET_PLUS_BUTTON_HEIGHT,
                GUI_TEXTURE,
                67,
                193,
                TEXTURE_TOTAL_WIDTH,
                TEXTURE_TOTAL_HEIGHT,
                btn -> onPress.execute(this));
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{button};
    }
}
