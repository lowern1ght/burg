package org.dawnoftime.onceuponatown.culture;

import org.dawnoftime.onceuponatown.building.type.BuildingType;

import java.util.List;

public class Specialization {
    private String id;
    private int color;
    private List<Step> steps;

    Specialization(String id) {
        this.id = id;
    }

    private record Step(int scoreNeeded, List<BuildingType> unlockedBuildings) {}
}
