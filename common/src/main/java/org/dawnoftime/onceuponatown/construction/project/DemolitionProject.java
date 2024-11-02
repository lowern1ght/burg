package org.dawnoftime.onceuponatown.construction.project;

import org.dawnoftime.onceuponatown.town.building.Building;
import org.dawnoftime.onceuponatown.construction.BuildingPlacementSettings;
import net.minecraft.server.level.ServerLevel;

public class DemolitionProject extends ConstructionProject {
    private Building toDemolish;
    private RemovingEntitiesPhase phase1;
    private RemovingBlocksPhase phase2;

    protected DemolitionProject(ServerLevel level, String name, BuildingPlacementSettings settings) {
        super(level, name, settings);
    }
}
