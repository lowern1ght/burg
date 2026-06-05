package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.jetbrains.annotations.NotNull;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class EditBoxAndConfirmWidgetCC extends WidgetCC {

    private final IconButton button;
    private final EditBox editBox;
    private final boolean naturalLanguage;

    /**
     * Adds a row to the screen that an editBox, with a confirm button next to it.
     *
     * @param posX            X position of the GUI.
     * @param hint            Component that will be displayed in the editBox to help the user knowing what to write.
     * @param naturalLanguage True to allow any wording in the text, false to standardize the text.
     * @param onPress         OnPress effect of the confirm button.
     */
    public EditBoxAndConfirmWidgetCC(int posX, Component hint, Font font, boolean naturalLanguage, WidgetAction onPress) {
        this.naturalLanguage = naturalLanguage;
        this.button = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 83, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> onPress.execute(this));
        button.active = false;
        this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + 1, 0, WIDGET_ZONE_WIDTH - WIDGET_HEIGHT - 3, WIDGET_HEIGHT - 2, hint) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                boolean b = super.keyPressed(keyCode, scanCode, modifiers);
                button.active = !this.getValue().trim().isEmpty();
                return b;
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (!naturalLanguage) {
                    if ((codePoint < 'a' || codePoint > 'z') &&
                            (codePoint < 'A' || codePoint > 'Z') &&
                            (codePoint < '0' || codePoint > '9') &&
                            codePoint != ' ' && codePoint != '_') {
                        return false; // Block other characters
                    }
                }
                return super.charTyped(codePoint, modifiers);
            }

            @Override
            public void insertText(@NotNull String textToWrite) {
                super.insertText(textToWrite.replace(" ", "_").toLowerCase());
            }
        };
        editBox.setHint(hint);
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{editBox, button};
    }

    @Override
    public @NotNull String get(String key) {
        String content = editBox.getValue().trim();
        if (!naturalLanguage) {
            content = content.toLowerCase().replace(" ", "_").replaceAll("[^a-z0-9_]", "");
        }
        return content;
    }

    @Override
    public WidgetCC set(String key, @NotNull String value) {
        editBox.setValue(value);
        return this;
    }
}
