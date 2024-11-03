package org.dawnoftime.onceuponatown.town.building;

import org.dawnoftime.onceuponatown.construction.BuildingPlacementSettings;
import org.dawnoftime.onceuponatown.town.building.placement.BuildPlacement;
import org.dawnoftime.onceuponatown.town.building.type.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Building {
    private final BuildingType buildingType;
    //private final BuildPlacement buildPlacement;
    private ResourceLocation structurePath;
    private BuildingPlacementSettings placementSettings;
    private BlockPos position;
    private Rotation rotation;
    private final List<BlockPos> sleepPositions = new ArrayList<>();
    private final List<BlockPos> workPositions = new ArrayList<>();
    private int level;

    private Building(BuildingType buildingType) {
        this.buildingType = buildingType;
        //this.buildPlacement = buildPlacement;
    }

    public static Building create(BuildingType buildingType) {
        return new Building(buildingType);
    }

    public static Building loadBuilding() {
        return null;
    }

    public void saveNBT(CompoundTag tag) {

    }

    public BlockPos getPosition() {
        return position;
    }

    public ResourceLocation getStructurePath() {
        return structurePath;
    }

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.buildingType.getProduction();
    }

    public int getLevel() {
        return level;
    }

    public BuildingType getType() {
        return buildingType;
    }

    public BuildingPlacementSettings getPlacementSettings() {
        return placementSettings;
    }
}
