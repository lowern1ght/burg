package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class SelectBoundingBoxWidgetCC extends WidgetCC {

    private final LeftAlignTextButton textButton;
    private final IconButton firstIconButton;
    private final IconButton secondIconButton;
    /**
     *
     * @param posX X position of the GUI.
     */
    public SelectBoundingBoxWidgetCC(
            int posX,
            Component text,
            Component firstTooltip,
            WidgetAction firstOnPress,
            Component secondTooltip,
            WidgetAction secondOnPress) {
        this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 2 * WIDGET_HEIGHT - 2, WIDGET_HEIGHT, text);
        this.firstIconButton = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - 2 * WIDGET_HEIGHT - 1, 0, WIDGET_HEIGHT, GUI_TEXTURE, 143, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> firstOnPress.execute(this));
        this.firstIconButton.setTooltip(Tooltip.create(firstTooltip));
        this.secondIconButton = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 163, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> secondOnPress.execute(this));
        this.secondIconButton.setTooltip(Tooltip.create(secondTooltip));
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{textButton, firstIconButton, secondIconButton};
    }
}
