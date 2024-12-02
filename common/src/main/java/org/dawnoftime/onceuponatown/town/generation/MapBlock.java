package org.dawnoftime.onceuponatown.town.generation;

import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.jetbrains.annotations.Nullable;

public interface MapBlock {

    default @Nullable Build<? extends BuildType> getBuild(){
        return null;
    }

    /**
     * @return A float value with the integer part specific to the Build class, and the decimal part specific to the instance.
     */
    float getMapFloat();
}
