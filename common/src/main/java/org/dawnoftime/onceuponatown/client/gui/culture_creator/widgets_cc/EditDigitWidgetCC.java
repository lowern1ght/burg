package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;
import org.jetbrains.annotations.NotNull;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class EditDigitWidgetCC extends WidgetCC {

    private final LeftAlignTextButton textButton;
    private final EditBox editBox;

    /**
     * Adds a row to the screen that an editBox, with a confirm button next to it.
     *
     * @param posX X position of the GUI.
     * @param hint Component that will be displayed in the editBox to help the user knowing what to write.
     */
    public EditDigitWidgetCC(int posX, Component hint, Font font, boolean isInt) {
        this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT - 1, WIDGET_HEIGHT, hint);
        this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - 3 * WIDGET_HEIGHT + 1, 0, 3 * WIDGET_HEIGHT - 2, WIDGET_HEIGHT - 2, EMPTY_EDIT_BOX) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (!Character.isDigit(codePoint)) {
                    if (isInt || (codePoint != '.' && codePoint != '-')) {
                        return false; // Block other characters
                    }
                }
                return super.charTyped(codePoint, modifiers);
            }
        };
        this.editBox.setHint(EMPTY_EDIT_BOX);
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{textButton, editBox};
    }

    @Override
    public @NotNull String get(String key) {
        return editBox.getValue().trim();
    }

    @Override
    public WidgetCC set(String key, @NotNull String value) {
        editBox.setValue(value);
        return this;
    }
}
