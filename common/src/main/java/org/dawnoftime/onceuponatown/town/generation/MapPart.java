package org.dawnoftime.onceuponatown.town.generation;

import org.dawnoftime.onceuponatown.building.instance.Build;
import org.jetbrains.annotations.Nullable;

public interface MapPart {
    @Nullable Build getBuild();
}
