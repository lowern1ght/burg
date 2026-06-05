package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Profession;

import java.util.HashMap;
import java.util.List;

public abstract class BuildType {
    public static final BuildType DEFAULT = new BuildType("default_build_type", 0, List.of(new BuildLevel(1, 1, 0, new HashMap<>(), 0)), BuildingPurpose.MISCELLANEOUS) {
    };
    private final String id;
    private final int weight;
    private final HashMap<String, BuildVariant> buildVariants = new HashMap<>();
    private final List<BuildLevel> levels;
    public final BuildingPurpose purpose;

    protected BuildType(String id, int weight, List<BuildLevel> levels, BuildingPurpose purpose) {
        this.id = id;
        this.weight = weight;
        this.levels = levels;
        this.purpose = purpose;
    }

    public static class CorruptedBuildType extends BuildType {
        protected CorruptedBuildType(String corruptedId) {
            super(corruptedId, 0, List.of(), BuildingPurpose.MISCELLANEOUS);
        }
    }

    public boolean isValid(String cultureId) {
        if (buildVariants.isEmpty()) {
            // No throw but potential errors since build type does not have any build variants
            Ouat.error(new CorruptedCultureException(cultureId, "Build type '%s' does not have any build variants".formatted(id)).getMessage());
            return false;
        } else {
            return true;
        }
    }

    protected void addVariant(BuildVariant variant, String shape, String cultureId) {
        buildVariants.put(variant.getId(), variant);
    }

    public String getId() {
        return id;
    }

    public int getWeight() {
        return weight;
    }

    public HashMap<String, BuildVariant> getBuildVariants() {
        return buildVariants;
    }

    public List<BuildLevel> getLevels() {
        return levels;
    }

    public record BuildLevel(int level, int requiredEra, int experienceGain, HashMap<Profession, Integer> workingSlots,
                             int dwellingSlots) {
    }

    @Override
    public String toString() {
        return "BuildType[" + id + "]";
    }
}
