package org.dawnoftime.onceuponatown.culture;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;

public class BuildingType {
    public static final BuildingType DEFAULT_TYPE = new BuildingType(null);
    private String id;
    private ResourceLocation structureFile;
    private Vec3i dimensions;
    private HashMap<ResourceLocation, Integer> production;
    private int weight;
    private HashMap<Orientation, Integer> researchGain;
    private HashMap<CitizenJob, Integer> citizenJobs;
    private List<BuildingLevel> levels;

    BuildingType(HashMap<ResourceLocation, Integer> production) {
        this.production = production;
    }

    public HashMap<ResourceLocation, Integer> getProduction() {
        return this.production;
    }

    public record BuildingLevel(
            int unlockEra,
            HashMap<ResourceLocation, Integer> constructionCost,
            int experienceGain,
            HashMap<Vec3i, CitizenAction> actionsPos
    ) {}
}
