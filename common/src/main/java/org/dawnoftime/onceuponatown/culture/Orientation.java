package org.dawnoftime.onceuponatown.culture;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.building.type.BuildType;

import java.util.List;

public class Orientation {
    private String id;
    private int color;
    private ResourceLocation logo;
    private List<BuildType> townHalls;
    private List<Step> steps;

    Orientation(String id) {
        this.id = id;
    }

    private record Step(int order, int unlockCost, List<BuildType> unlockedBuildings) {}
}
