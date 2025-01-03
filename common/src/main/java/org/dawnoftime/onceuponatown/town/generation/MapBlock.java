package org.dawnoftime.onceuponatown.town.generation;

import org.dawnoftime.onceuponatown.building.NpcBuild;
import org.jetbrains.annotations.Nullable;

public interface MapBlock {

    default @Nullable NpcBuild getBuild() {
        return null;
    }
}
