package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class CoordinatesWidgetCC extends WidgetCC {

    private final LeftAlignTextButton xText;
    private final EditBox xValue;
    private final LeftAlignTextButton yText;
    private final EditBox yValue;
    private final LeftAlignTextButton zText;
    private final EditBox zValue;
    /**
     *
     * @param posX X position of the GUI.
     */
    public CoordinatesWidgetCC(int posX, Font font) {
        int textBoxWidth = 20;
        int editBoxWidth = 46;
        int pairWidth = textBoxWidth + editBoxWidth + 5;
        this.xText = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, textBoxWidth, WIDGET_HEIGHT, Component.literal("X"));
        this.xValue = this.createDigitEditBox(posX + WIDGET_ZONE_X + textBoxWidth + 2, editBoxWidth, font);
        this.yText = new LeftAlignTextButton(posX + WIDGET_ZONE_X + pairWidth, 0, textBoxWidth, WIDGET_HEIGHT, Component.literal("Y"));
        this.yValue = this.createDigitEditBox(posX + WIDGET_ZONE_X + pairWidth + textBoxWidth + 2, editBoxWidth, font);
        this.zText = new LeftAlignTextButton(posX + WIDGET_ZONE_X + 2 * pairWidth, 0, textBoxWidth, WIDGET_HEIGHT, Component.literal("Z"));
        this.zValue = this.createDigitEditBox(posX + WIDGET_ZONE_X + 2 * pairWidth + textBoxWidth + 2, editBoxWidth, font);
    }

    private EditBox createDigitEditBox(int x, int width, Font font) {
        EditBox box = new EditBox(font, x, 0, width, WIDGET_HEIGHT - 2, EMPTY_EDIT_BOX) {
            // We must edit the setY function because for some reason, MC devs decided that the actual border of this widget should be bigger than its size...
            @Override
            public void setY(int y) {
                super.setY(y + 1);
            }

            @Override
            public boolean charTyped(char codePoint, int modifiers) {
                if (!Character.isDigit(codePoint)) {
                    return false;
                }
                return super.charTyped(codePoint, modifiers);
            }
        };
        box.setHint(EMPTY_EDIT_BOX);
        return box;
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{xText, xValue, yText, yValue, zText, zValue};
    }
}
