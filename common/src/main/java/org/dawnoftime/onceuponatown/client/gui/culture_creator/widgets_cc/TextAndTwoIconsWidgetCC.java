package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.gui.widgets.IconButton;
import org.dawnoftime.onceuponatown.client.gui.widgets.LeftAlignTextButton;
import org.jetbrains.annotations.NotNull;

import static org.dawnoftime.onceuponatown.client.gui.culture_creator.BaseCCScreen.*;

public class TextAndTwoIconsWidgetCC extends WidgetCC {

    private final LeftAlignTextButton textButton;
    private final IconButton firstIconButton;
    private final IconButton secondIconButton;
    /**
     *
     * @param posX X position of the GUI.
     */
    public TextAndTwoIconsWidgetCC(
            int posX,
            Component text,
            ResourceLocation textureRL,
            int textureWidth,
            int textureHeight,
            Component firstHover,
            int firstU,
            int firstV,
            WidgetAction firstOnPress,
            Component secondHover,
            int secondU,
            int secondV,
            WidgetAction secondOnPress) {
        this.textButton = new LeftAlignTextButton(posX + WIDGET_ZONE_X, 0, WIDGET_ZONE_WIDTH - 2 * WIDGET_HEIGHT - 2, WIDGET_HEIGHT, text);
        this.firstIconButton = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, textureRL, firstU, firstV, textureWidth, textureHeight, null);
        this.secondIconButton = new IconButton(posX + WIDGET_ZONE_X + WIDGET_ZONE_WIDTH - WIDGET_HEIGHT, 0, WIDGET_HEIGHT, textureRL, firstU, firstV, textureWidth, textureHeight, null);
    }

    @Override
    public AbstractWidget[] getWidgets() {
        return new AbstractWidget[]{textButton, firstIconButton, secondIconButton};
    }
}
