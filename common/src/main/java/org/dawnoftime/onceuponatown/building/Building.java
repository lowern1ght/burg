package org.dawnoftime.onceuponatown.building;

import org.dawnoftime.onceuponatown.building.placement.BuildPlacement;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Building {
    private final BuildType buildingType;
    private final BuildVariant variant;
    private final BuildPlacement buildPlacement;
    private ResourceLocation structurePath;
    private BuildingPlacementSettings placementSettings;
    private BlockPos position;
    private Rotation rotation;
    private final List<BlockPos> sleepPositions = new ArrayList<>();
    private final List<BlockPos> workPositions = new ArrayList<>();
    private int level;

    private Building(BuildType buildingType, BuildVariant variant, BuildPlacement buildPlacement) {
        this.buildingType = buildingType;
        this.variant = variant;
        this.buildPlacement = buildPlacement;
    }

    public BuildVariant getVariant() {
        return this.variant;
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

    public BuildType getType() {
        return buildingType;
    }

    public BuildingPlacementSettings getPlacementSettings() {
        return placementSettings;
    }
}
