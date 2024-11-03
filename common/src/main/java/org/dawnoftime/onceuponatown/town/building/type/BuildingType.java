package org.dawnoftime.onceuponatown.town.building.type;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.culture.Orientation;
import oshi.util.tuples.Pair;

import java.util.HashMap;
import java.util.List;

public class BuildingType {
    public static final BuildingType DEFAULT_TYPE = new BuildingType(null);
    private String id;
    private Vec3i dimensions;
    private HashMap<ResourceLocation, Integer> production;
    private int weight;
    private HashMap<Orientation, Integer> researchGain;
    private HashMap<NpcJob, Integer> npcJobs;
    private List<BuildingLevel> levels;

    BuildingType(HashMap<ResourceLocation, Integer> production) {
        this.production = production;
    }

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.production;
    }

    public ResourceLocation getStructureFileForLevel(int buildingLevel) {
        return levels.get(buildingLevel).structureFile;
    }

    public record BuildingLevel(
            ResourceLocation structureFile,
            int unlockEra,
            Pair<Orientation, Integer> requirement,
            int experienceGain,
            HashMap<Vec3i, NpcAction> actionsPos
    ) {}
}
