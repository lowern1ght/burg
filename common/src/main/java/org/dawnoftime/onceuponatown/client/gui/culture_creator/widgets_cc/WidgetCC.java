package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import org.jetbrains.annotations.NotNull;

public abstract class WidgetCC {

    public WidgetCC() {}

    @NotNull
    public String get() {
        return "";
    }

    public void set(@NotNull String value) {}

    public abstract AbstractWidget[] getWidgets();

    @FunctionalInterface
    public interface WidgetAction {
        void execute(WidgetCC widget);
    }

    /*
    protected void createNewRawButton(Button.OnPress onPressConfirm) {
        IconButton button = new IconButton(posX + WIDGET_ZONE_X + (WIDGET_ZONE_WIDTH / 2) - (WIDGET_PLUS_BUTTON_HEIGHT /2), (WIDGET_HEIGHT - WIDGET_PLUS_BUTTON_HEIGHT) / 2, WIDGET_PLUS_BUTTON_HEIGHT, GUI_TEXTURE, 67, 193, TEXTURE_TOTAL_WIDTH, TEXTURE_TOTAL_HEIGHT, onPressConfirm);
        widgets.add(new AbstractWidget[]{button});
        this.addRenderableWidget(button);
    }
     */
}

