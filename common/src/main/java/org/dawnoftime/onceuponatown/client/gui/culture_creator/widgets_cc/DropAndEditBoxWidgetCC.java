package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.DropDownButton;

import java.util.HashMap;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class DropAndEditBoxWidgetCC extends WidgetCC {

    private final DropDownButton dropDownButton;

    /**
     * Adds a row to the screen that contains a dropdown on the left, and an edit box on the right.
     *
     * @param posX    X position of the GUI.
     */
    public DropAndEditBoxWidgetCC(int posX, Component text) {
        this.dropDownButton = new DropDownButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT, text, new HashMap<>());
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{dropDownButton};
    }
}
