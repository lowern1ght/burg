package org.dawnoftime.onceuponatown.culture;

import org.dawnoftime.onceuponatown.building.type.BuildingType;

import java.util.List;

public class Specialization {
    private final String id;
    private final int color;
    private List<Step> steps;

    Specialization(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String getId() {
        return id;
    }

    public int getColor() {
        return color;
    }

    public record Step(int requiredScore, List<BuildingType> unlockedBuildings) {}
}
