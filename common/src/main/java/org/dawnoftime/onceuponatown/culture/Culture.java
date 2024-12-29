package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;
import oshi.util.tuples.Triplet;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static org.dawnoftime.onceuponatown.culture.CultureManager.CULTURE_JSON_FILE_NAME;
import static org.dawnoftime.onceuponatown.culture.CultureManager.CULTURE_FOLDER_NAME;

public class Culture {
    // TODO Add some code to manage when a map was saved with a different version of the culture datapack (ie. a BuildType was removed).
    public static final Culture DEFAULT_CULTURE = new Culture("default_culture", List.of(), List.of());
    public static final String ROAD_TYPE_NAME = "road";
    public static final String WIDE_ROAD_TYPE_NAME = "wide_road";
    public static final String BRIDGE_TYPE_NAME = "bridge";
    public static final String WALL_TYPE_NAME = "wall";
    private final String id;
    private final HashMap<String, BuildType> buildTypeMap = new HashMap<>();
    private final HashMap<String, Pair<Integer, Integer>> starterPack = new HashMap<>();
    private final List<Era> eras;
    private final List<String> specializations;
    private List<Item> foods;
    private boolean corrupted;

    private Culture(String id, List<Era> eras, List<String> specializations) {
        this.id = id;
        this.eras = eras;
        this.specializations = specializations;
    }

    public static @Nullable Culture createCulture(String cultureId, ResourceLocation fileLocation, Resource cultureJsonResource, ResourceManager resourceManager) {
        Ouat.info("Loading culture '" + cultureId + "'...");
        JsonObject cultureJsonObject;
        try (Reader reader = cultureJsonResource.openAsReader()) {
            cultureJsonObject = GsonHelper.parse(reader);
            // Id
            String id = cultureJsonObject.get("id").getAsString();
            if (id == null || !id.equals(cultureId)) {
                throw new CorruptedCultureException(cultureId, "Failed to load a culture. Culture's id '%s' does not match the culture's folder name".formatted(id, CULTURE_JSON_FILE_NAME));
            }
            // Eras
            List<Culture.Era> eras = readEras(cultureJsonObject, cultureId);
            // Specializations
            List<String> specializationsIds = readSpecializationsIds(cultureJsonObject, cultureId);

            Culture culture = new Culture(cultureId, eras, specializationsIds);
            // Mandatory BuildType
            culture.addBuildType(new SliceBuildType(ROAD_TYPE_NAME));
            culture.addBuildType(new SliceBuildType(WIDE_ROAD_TYPE_NAME));
            //culture.addBuildType(new SliceBuildType(BRIDGE_TYPE_NAME));
            //culture.addBuildType(new SliceBuildType(WALL_TYPE_NAME));

            // BuildTypes
            var buildResources = resourceManager.listResources(CULTURE_FOLDER_NAME + "/" + cultureId + "/builds/build_type", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                BuildType buildingType = BuildingType.createFromJson(resourceManager, buildResource, cultureId);
                if (buildingType != null) {
                    culture.addBuildType(buildingType);
                }
            });

            // BuildVariants
            buildResources = resourceManager.listResources(CULTURE_FOLDER_NAME + "/" + cultureId + "/builds/build_variant", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                Triplet<String, BuildVariant, String> variant = BuildVariant.createFromDataPack(resourceManager, buildResource, cultureId);
                if (variant != null) {
                    culture.addBuildVariant(variant.getA(), variant.getB(), variant.getC());
                }
            });

            // Removing any BuildType that don't have any variant since they can't be built.
            culture.dropBuildTypeWithoutVariant();

            // Load the starter pack
            JsonElement elem = CultureManager.tryGet(cultureId, cultureJsonObject, "Culture", "starter_pack", CULTURE_JSON_FILE_NAME, fileLocation);
            JsonArray array = elem.getAsJsonArray();
            for(JsonElement arrayElem : array) {
                JsonObject subObject = arrayElem.getAsJsonObject();
                String buildTypeId = CultureManager.tryGet(cultureId, subObject, "Culture", "build_type", " in an object in the section 'starter_pack'", CULTURE_JSON_FILE_NAME, fileLocation).getAsString();
                if (!culture.buildTypeMap.containsKey(buildTypeId)){
                    throw new CorruptedCultureException(cultureId, "Failed to load a culture. The build_type '%s' in the starter pack is unknown, please check this file: %s".formatted(buildTypeId, CULTURE_JSON_FILE_NAME));
                }
                int min = CultureManager.tryGet(cultureId, subObject, "Culture", "min", " in an object in the section 'starter_pack'", CULTURE_JSON_FILE_NAME, fileLocation).getAsInt();
                int max = CultureManager.tryGet(cultureId, subObject, "Culture", "max", " in an object in the section 'starter_pack'", CULTURE_JSON_FILE_NAME, fileLocation).getAsInt();
                if (max < min) {
                    throw new CorruptedCultureException(cultureId, "Failed to load a culture. Check the values of the minimum and maximum number of the build_type '%s' in the starter pack in this file: %s".formatted(buildTypeId, fileLocation));
                }
                culture.addStarterPackBuild(buildTypeId, min, max);
            }
            return culture;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CorruptedCultureException e){
            Ouat.error(e.getMessage());
            return null;
        }
    }

    private static List<Culture.Era> readEras(JsonObject cultureJsonObject, String cultureId) throws CorruptedCultureException {
        List<Culture.Era> eras = new ArrayList<>();
        var eraArray = cultureJsonObject.getAsJsonArray("eras");
        if (eraArray == null) {
            throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "eras", "Missing eras definition");
        }
        int eraIndex = 1;
        for (int i = 0; i < eraArray.size(); ++i) {
            var jsonObject = eraArray.get(i).getAsJsonObject();
            int xpNeeded = jsonObject.get("required_experience").getAsInt();
            if (xpNeeded < 0) { // Error : required xp can not be negative
                throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "era required_experience", "Era required_experience can not be negative");
            }
            int maxClutter = jsonObject.get("max_buildings_weight").getAsInt();
            if (maxClutter <= 0) { // Error : max clutter can not be null or negative
                throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "era max_buildings_weight", "max_buildings_weight can not be negative");
            }
            eras.add(new Culture.Era(eraIndex, xpNeeded, maxClutter));
            ++eraIndex;
        }
        return eras;
    }

    private static List<String> readSpecializationsIds(JsonObject cultureJsonObject, String cultureId) throws CorruptedCultureException {
        List<String> specializationsIds = new ArrayList<>();
        var specializations = cultureJsonObject.getAsJsonArray("specializations");
        if (specializations == null) {
            throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "specializations", "Missing culture's specializations definition");
        }
        specializations.forEach((jsonElement -> {
            String specializationId = getSpecializationId(cultureId, jsonElement);
            specializationsIds.forEach((id) -> {
                if (id.equals(specializationId)) { // Error : duplicate orientation id
                    throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "specializations", "Multiple specializations share the same id");
                }
            });
            specializationsIds.add(specializationId);
        }));
        return specializationsIds;
    }

    @NotNull
    private static String getSpecializationId(String cultureId, JsonElement jsonElement) throws CorruptedCultureException {
        var jsonObject = jsonElement.getAsJsonObject();
        String specializationId = jsonObject.get("id").getAsString();
        if (specializationId == null ) { // Error : missing orientation id
            throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "specializations", "Missing a specialization id");
        }
        if (specializationId.isBlank()) { // Error : invalid orientation id
            throw new CorruptedCultureException(cultureId, CULTURE_JSON_FILE_NAME, "specializations", "Invalid specialization id");
        }
        return specializationId;
    }

    private void addBuildType(@NotNull BuildType type) {
        buildTypeMap.put(type.getId(), type);
    }

    public BuildType getBuildType(String buildTypeId){
        //TODO return default build type in case of invalid parameter
        return buildTypeMap.get(buildTypeId);
    }

    private void dropBuildTypeWithoutVariant(){
        if (buildTypeMap.entrySet().removeIf(entry -> {
            boolean valid = entry.getValue().isValid(id);
            return !valid;
        })) {
            Ouat.error("Culture [%s]: Removed one or more build types as they don't have any build_variant.".formatted(id));
        }
    }

    public void markCorrupted() {
        corrupted = true;
    }

    private void addBuildVariant(@NotNull String buildTypeId, @NotNull BuildVariant variant, @NotNull String shape) {
        BuildType type = buildTypeMap.get(buildTypeId);
        if (type != null) {
            type.addVariant(variant, shape, id);
        } else {
            Ouat.error("Culture [%s]: Failed to register the build_variant '%s'. Its associated build_type '%s' is not defined for this culture.".formatted(id, variant.getId(), buildTypeId));
        }
    }

    private void addStarterPackBuild(String buildTypeId, int min, int max){
        starterPack.put(buildTypeId, new Pair<>(min, max));
    }

    /**
     * Returns a list that contains all the BuildTypes that should be built in a random index.
     * @param rand RandomSource used to roll the number of each BuildType.
     * @return The list of BuildType to build.
     */
    public List<BuildingType> getRandomStarterPack(RandomSource rand) {
        List<BuildingType> types = new ArrayList<>();
        for (String buildTypeName: starterPack.keySet()) {
            Pair<Integer, Integer> range = starterPack.get(buildTypeName);
            BuildType type = buildTypeMap.get(buildTypeName);
            if (type instanceof BuildingType buildingType) {
                int times = rand.nextIntBetweenInclusive(range.getA(), range.getB());
                for (int i = 0; i < times; ++i) {
                    types.add(buildingType);
                }
            } else {
                //throw new CorruptedCultureException(id, "Wrong build type in the culture's starterpack : '%s' is not a build from this culture's datapack.".formatted(buildTypeName));
                Ouat.error(new CorruptedCultureException(id, "Wrong build type in the culture's starterpack : '%s' is not a build from this culture's datapack.".formatted(buildTypeName)).getMessage());
            }
        }
        Collections.shuffle(types); // So that the builds do not always spawn in the same sequence
        return types;
    }

    public List<Era> getEras() {
        return eras;
    }

    public List<String> getSpecializations() {
        return specializations;
    }

    public HashMap<String, Pair<Integer, Integer>> getStarterPack() {
        return starterPack;
    }

    public String getId() {
        return id;
    }

    public List<BuildType> getBuildTypes() {
        return buildTypeMap.values().stream().toList();
    }

    public record Era(int index, int requiredXp, int buildingsWeight) {}
}
