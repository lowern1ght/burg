package org.dawnoftime.onceuponatown.construction.project;

import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.server.level.ServerLevel;

public class RepairProject extends ConstructionProject{
    private Building toRepair;

    private RemovingEntitiesPhase phase1;
    private RemovingBlocksPhase phase2;
    private PlacingBlocksPhase phase3;
    private PlacingEntitiesPhase phase4;

    protected RepairProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        super(level, name, settings);
    }
}
