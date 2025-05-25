package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.DropdownButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class DropAndEditBoxWidgetCC extends WidgetCC {

    private final DropdownButton dropDownButton;
    private final EditBox editBox;

    /**
     * Adds a row to the screen that contains a dropdown on the left, and an edit box on the right.
     *
     * @param posX    X position of the GUI.
     * @param storeChoiceFunction consumer that takes the key and displayed values to store it at the proper place (most likely in a screen parameter).
     * @param options Map that contains a String key associated to its displayed text.
     */
    public DropAndEditBoxWidgetCC(int posX, Screen screen, Component text, Font font, BiConsumer<String, String> storeChoiceFunction, LinkedHashMap<String, String> options) {
        this.dropDownButton = new DropdownButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT - 1, WIDGET_HEIGHT, screen, text, options, storeChoiceFunction);
        this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT + 1, 0, 3 * WIDGET_HEIGHT - 2, WIDGET_HEIGHT - 2, EMPTY_EDIT_BOX) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (!Character.isDigit(codePoint)) {
                    return false; // Block other characters
                }
                return super.charTyped(codePoint, modifiers);
            }
        };
        this.editBox.setHint(EMPTY_EDIT_BOX);
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{dropDownButton, editBox};
    }

    @Override
    public WidgetCC set(@Nullable String key, @NotNull String value) {
        if ("selection".equals(key)) {
            dropDownButton.setCurrentChoiceKey(value);
        }
        if ("number".equals(key)) {
            editBox.setValue(value);
        }
        return this;
    }

    @Override
    public @NotNull String get(@Nullable String key) {
        if ("number".equals(key)) {
            return editBox.getValue().trim();
        }
        return dropDownButton.getCurrentChoiceKey();
    }
}
