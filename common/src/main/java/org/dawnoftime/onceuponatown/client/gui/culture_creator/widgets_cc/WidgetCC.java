package org.dawnoftime.onceuponatown.client.gui.culture_creator.widgets_cc;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class WidgetCC {
    protected static final Component EMPTY_EDIT_BOX = Component.literal("...");

    public WidgetCC() {}

    @NotNull
    public String get() {
        return this.get(null);
    }

    @NotNull
    public String get(@Nullable String key) {
        return "";
    }

    public WidgetCC set(@NotNull String value) {
        return this.set(null, value);
    }

    public WidgetCC set(@Nullable String key, @NotNull String value) {
        return this;
    }

    public abstract AbstractWidget[] getWidgets();

    @FunctionalInterface
    public interface WidgetAction {
        void execute(WidgetCC widgetCC);
    }
}

