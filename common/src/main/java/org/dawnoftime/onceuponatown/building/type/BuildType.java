package org.dawnoftime.onceuponatown.building.type;

import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.entity.Profession;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;

public abstract class BuildType {
    private final String id;
    private final int weight;
    private final HashMap<String, BuildVariant> buildVariants = new HashMap<>();
    private final List<BuildLevel> levels;

    protected BuildType(String id, int weight, List<BuildLevel> levels) {
        this.id = id;
        this.weight = weight;
        this.levels = levels;
    }

    public boolean isValid(String cultureId) {
        if (buildVariants.isEmpty()) {
            Ouat.error("Culture '%s' is corrupted. Build type '%s' does not have any build variants".formatted(cultureId, id));
            return false;
        } else {
            return true;
        }
    }

    public void addVariant(BuildVariant variant, String shape, String cultureId) {
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

    public record BuildLevel(int level, int requiredEra, int xpGain, @Nullable List<Profession> professions) {}
}
