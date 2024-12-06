package org.dawnoftime.onceuponatown.town.generation;

import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.jetbrains.annotations.Nullable;

public interface MapBlock {

    default @Nullable Build getBuild(){
        return null;
    }
}
