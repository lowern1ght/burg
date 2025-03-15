package org.dawnoftime.onceuponatown.town.generation;

import org.dawnoftime.onceuponatown.building.Build;
import org.jetbrains.annotations.Nullable;

public enum FreeSpace implements MapPart {
    LAND,
    HOLE,
    PEAK,
    WATER,
    LAVA;

    @Override
    public @Nullable Build getBuild() {
        return null;
    }
}
