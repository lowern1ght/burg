package com.dotteam.onceuponatown.culture;

import net.minecraft.world.item.Item;

import java.util.List;

public class Culture {
    public static final Culture FAKE_PLAINS = new Culture("fake_plains", 3, 14, null);
    private String id;
    private List<Orientation> orientations;
    private List<BuildingType> buildingTypes;
    private int starterPackMinSize;
    private int starterPackMaxSize;
    private List<Item> foods;
    private List<Era> eras;

    Culture(String id, int starterPackMinSize, int starterPackMaxSize, List<Item> foods) {
        this.id = id;
        this.starterPackMinSize = starterPackMinSize;
        this.starterPackMaxSize = starterPackMaxSize;
        this.foods = foods;
    }

    void addOrientation(Orientation orientation) {
        this.orientations.add(orientation);
    }

    void addEra(Era era) {
        this.eras.add(era);
    }

    void addBuildingType(BuildingType type) {
        this.buildingTypes.add(type);
    }

    public String getId() {
        return this.id;
    }

    record Era(int order, int requiredXp, int buildingsWeight) {}
}
