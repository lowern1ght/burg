package org.dawnoftime.onceuponatown.building.type;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.CultureFileHelper;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;

import java.util.*;

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

    protected static @NotNull BuildTypeCommonJsonData readJsonCommonData(String cultureId, String buildTypeId, JsonObject rootJson, CultureFileHelper helper, ResourceManager resourceManager) throws CorruptedCultureException {
        /* Reading mandatory id to avoid conflicts with other build types */
        if (!helper.getString(rootJson, "id").equals(buildTypeId)) {
            helper.throwInvalidField("id", "It should match the name of the build type json file.");
        }
        /* Reading weight */
        int weight = helper.getPositiveInt(rootJson, "weight");
        /* Reading levels */
        JsonArray levelsArray = helper.getJsonArray(rootJson, "levels");
        List<BuildLevel> levels = new ArrayList<>();
        String loc = "in levels[]";
        int level = 1;
        for (JsonElement je : levelsArray) {
            JsonObject levelJson = helper.asJsonObject(je, "levels[] element", loc);
            /* Reading required era */
            int requiredEra = helper.getPositiveInt(levelJson, "required_era", loc);
            // TODO read experience gain
            //int experienceGain = helper.getPositiveInt(elemJson, "experience_gain", loc);
            /* Reading optional dwelling slots */
            JsonElement dwellingSlotsElem = levelJson.get("dwelling_slots");
            int dwellingSlots = dwellingSlotsElem == null ? 0 : helper.getPositiveInt(levelJson, "dwelling_slots", loc);
            /* Reading working slots */
            HashMap<Profession, Integer> workingSlots = new HashMap<>();
            JsonElement workingSlotsElem = levelJson.get("working_slots");
            if (workingSlotsElem != null) {
                JsonArray workingSlotsArray = helper.getJsonArray(levelJson, "working_slots", loc);
                Set<String> professionsIds = new HashSet<>();
                loc = "in levels[working_slots[]]";
                for (JsonElement el : workingSlotsArray) {
                    JsonObject professionJson = helper.asJsonObject(el, "levels[working_slots[]] element", loc);
                    String professionId = helper.getString(professionJson, "profession", loc);
                    int slots = helper.getPositiveInt(professionJson, "slots", loc);
                    if (professionsIds.contains(professionId)) {
                        helper.throwInvalidField("profession", loc, "This profession is already defined here");
                    }
                    workingSlots.put(Profession.of(professionId), slots);
                    professionsIds.add(professionId);
                }
            }
            levels.add(new BuildLevel(level, requiredEra, 0, workingSlots, dwellingSlots));
            ++level;
        }
        if (levels.isEmpty()) {
            helper.throwInvalidField("levels", "It can't be empty. Each build type should have at least one level.");
        }
        /* Reading build variants */
        HashMap<String, Pair<BuildVariant, String>> variants = readBuildTypeVariants(cultureId, rootJson, helper, resourceManager);
        /* Finished reading build type */
        return new BuildTypeCommonJsonData(weight, levels, variants);
    }

    private static @NotNull HashMap<String, Pair<BuildVariant, String>> readBuildTypeVariants(String cultureId, JsonObject rootJson, CultureFileHelper helper, ResourceManager resourceManager) throws CorruptedCultureException {
        HashMap<String, Pair<BuildVariant, String>> variants = new HashMap<>();
        String loc = "in variants[]";
        /* Reading build variants */
        for (JsonElement je : helper.getJsonArray(rootJson, "variants")) {
            JsonObject variantJson = helper.asJsonObject(je, "variants[] element", loc);
            Pair<BuildVariant, String> variant = BuildVariant.createFromDataPack(cultureId, variantJson, helper, resourceManager);
            String variantId = variant.getA().getId();
            if (variants.containsKey(variantId)) {
                helper.throwInvalidField("name", loc, "Multiple variants share the same name '" + variantId + "'.");
            }
            variants.put(variant.getA().getId(), variant);
        }
        if (variants.isEmpty()) {
            helper.throwInvalidField("variants", "It can't be empty. Each build type must have at least one variant.");
        }
        return variants;
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

    protected record BuildTypeCommonJsonData(int weight, List<BuildLevel> levels,
                                             HashMap<String, Pair<BuildVariant, String>> variants) {
    }

    public record BuildLevel(int level, int requiredEra, int experienceGain, HashMap<Profession, Integer> workingSlots,
                             int dwellingSlots) {
    }

    @Override
    public String toString() {
        return "BuildType[" + id + "]";
    }
}
