package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.gui.widgets.ItemButton;
import org.jetbrains.annotations.NotNull;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class ItemEditBoxWidgetCC extends WidgetCC {

    private final EditBox editBox;
    private final ItemButton itemButton;
    private String customSuggestion;

    /**
     *
     * @param posX X position of the GUI.
     * @param hint Component that will be displayed in the editBox to help the user knowing what to write.
     */
    public ItemEditBoxWidgetCC(int posX, Component hint, Font font) {
        this.itemButton = new ItemButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 123, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, null, null);
        this.editBox = new EditBox(font, posX + WIDGET_ZONE_X + 1, 0, WIDGET_ZONE_WIDTH - WIDGET_HEIGHT - 3, WIDGET_HEIGHT - 2, hint) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }

            @Override
            public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
                // 258 = Tab key, auto-complete
                if (keyCode == 258 && customSuggestion != null) {
                    this.setValue(this.getValue() + customSuggestion);
                    customSuggestion = null;
                    return true;
                }
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        };
        editBox.setResponder(this::onItemInputChanged);
        editBox.setHint(hint);
    }

    private void onItemInputChanged(String input) {
        if (input == null || input.isEmpty()) {
            this.customSuggestion = null;
        } else {
            // Find the first matching item ID
            this.customSuggestion = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString)
                    .filter(id -> id.startsWith(input))
                    .findFirst()
                    .orElse(null);
        }
        if (this.customSuggestion != null) {
            this.customSuggestion = this.customSuggestion.substring(input.length());
        }
        this.editBox.setSuggestion(this.customSuggestion);
        this.itemButton.updateDisplayedItem(input);

    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{itemButton, editBox};
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
