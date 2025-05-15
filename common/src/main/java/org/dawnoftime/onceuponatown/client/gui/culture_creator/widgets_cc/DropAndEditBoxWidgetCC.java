package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.DropdownButton;

import java.util.HashMap;
import java.util.LinkedHashMap;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class DropAndEditBoxWidgetCC extends WidgetCC {

    private final DropdownButton dropDownButton;

    /**
     * Adds a row to the screen that contains a dropdown on the left, and an edit box on the right.
     *
     * @param posX    X position of the GUI.
     */
    public DropAndEditBoxWidgetCC(int posX, Screen screen, Component text) {
        LinkedHashMap<String, String> test = new LinkedHashMap<>();
        test.put("test0", "Test 0");
        test.put("test1", "Test 1");
        test.put("test2", "Test 2");
        test.put("test3", "Test 3");
        test.put("test4", "Test 4");
        test.put("test5", "Test 5");
        test.put("test6", "Test 6");
        this.dropDownButton = new DropdownButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH, WIDGET_HEIGHT, screen, text, test);
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{dropDownButton};
    }
}
