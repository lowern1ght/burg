package org.dawnoftime.onceuponatown.culture;

import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import oshi.util.tuples.Triplet;

import java.util.ArrayList;
import java.util.List;

public class Culture {
    public static final Culture FAKE_PLAINS = new Culture("fake_plains", 3, 14, null);
    private String id;
    private List<Orientation> orientations;
    private List<BuildingType> buildingTypes;
    private int starterPackMinSize;
    private int starterPackMaxSize;
    // Starter pack candidate, building type id, min amount, max amount
    private Triplet<BuildingType, Integer, Integer> starterPack;
    private List<Item> foods;
    private final List<Era> eras;

    Culture(String id, int starterPackMinSize, int starterPackMaxSize, List<Era> eras) {
        this.id = id;
        this.starterPackMinSize = starterPackMinSize;
        this.starterPackMaxSize = starterPackMaxSize;
        this.eras = eras;
    }

    public List<BuildingType> getRandomStarterPack() {
        return new ArrayList<>();
    }

    void addOrientation(Orientation orientation) {
        this.orientations.add(orientation);
    }

    public List<Era> getEras() {
        return this.eras;
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

    public int getStarterPackMinSize() {
        return this.starterPackMinSize;
    }

    public int getStarterPackMaxSize() {
        return this.starterPackMaxSize;
    }

    public record Era(int order, int requiredXp, int buildingsWeight) {}
}
