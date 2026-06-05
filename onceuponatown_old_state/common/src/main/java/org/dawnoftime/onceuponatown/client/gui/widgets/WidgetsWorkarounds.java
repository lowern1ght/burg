package org.dawnoftime.onceuponatown.client.gui.widgets;

public interface WidgetsWorkarounds {
    default boolean allowMouseReleased() {
        return false;
    }

    default boolean allowMouseScrolled() {
        return false;
    }
}
