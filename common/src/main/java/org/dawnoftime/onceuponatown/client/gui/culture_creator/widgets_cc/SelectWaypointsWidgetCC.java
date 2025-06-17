package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class SelectWaypointsWidgetCC extends WidgetCC {

    private final LeftAlignTextButton textButton;
    private final IconButton iconButton;
    /**
     *
     * @param posX X position of the GUI.
     */
    public SelectWaypointsWidgetCC(
            int posX,
            Component text,
            Component firstTooltip,
            WidgetAction firstOnPress) {
        this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - WIDGET_HEIGHT - 1, WIDGET_HEIGHT, text);
        this.iconButton = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, GUI_TEXTURE, 183, 166, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, btn -> firstOnPress.execute(this));
        this.iconButton.setTooltip(Tooltip.create(firstTooltip));
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{textButton, iconButton};
    }
}
