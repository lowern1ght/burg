package org.dawnoftime.onceuponatown.building;

import org.dawnoftime.onceuponatown.building.placement.BuildPlacement;
import org.dawnoftime.onceuponatown.building.placement.BuildingPlacement;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Building {
    private final BuildingType buildingType;
    private final BuildVariant variant;
    private final BuildPlacement placement;
    private ResourceLocation structurePath;
    private BuildingPlacement placementSettings;
    private BlockPos position;
    private Rotation rotation;
    private final List<BlockPos> sleepPositions = new ArrayList<>();
    private final List<BlockPos> workPositions = new ArrayList<>();
    private int level;

    private Building(BuildingType buildingType, BuildVariant variant, BuildPlacement placement) {
        this.buildingType = buildingType;
        this.variant = variant;
        this.placement = placement;
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

    public BuildingType getType() {
        return buildingType;
    }

    public BuildPlacement getPlacement() {
        return this.placement;
    }
}
