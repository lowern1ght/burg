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
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.building.type.RoadType;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_JSON_FILE_NAME;
import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_FOLDER_NAME;

public class Culture {
    // TODO manage default culture in case of corrupted culture files or town files
    public static final String ROAD_TYPE_NAME = "road";
    public static final String WIDE_ROAD_TYPE_NAME = "wide_road";
    public static final String BRIDGE_TYPE_NAME = "bridge";
    public static final String WALL_TYPE_NAME = "wall";
    private final String id;
    private final List<Specialization> specializations;
    private final HashMap<String, BuildType> buildTypes;
    private final HashMap<String, Pair<Integer, Integer>> starterPack;
    private final List<Era> eras;
    //private final List<Item> foods; TODO read foods
    public static final Culture DEFAULT_CULTURE = new Culture("default_culture", List.of(new Specialization("default_specialization")), new HashMap<>(), new HashMap<>(), List.of(new Era(1, 0, Integer.MAX_VALUE))) {
        @Override
        public List<BuildingType> getRandomStarterPack(RandomSource rand) {
            return new ArrayList<>();
        }

        @Override
        public BuildType getBuildType(String typeId) {
            return BuildType.DEFAULT;
        }
    };

    private Culture(String id, List<Specialization> specializations, HashMap<String, BuildType> buildTypes, HashMap<String, Pair<Integer, Integer>> starterPack, List<Era> eras) {
        this.id = id;
        this.specializations = specializations;
        this.buildTypes = buildTypes;
        this.starterPack = starterPack;
        this.eras = eras;
        //this.foods = foods;
    }

    public static @Nullable Culture readCultureFromDataPack(String detectedId, ResourceLocation jsonFileLocation, Resource jsonFileResource, ResourceManager resourceManager) {
        Ouat.info("Loading culture '" + detectedId + "'");
        try (Reader reader = jsonFileResource.openAsReader()) {
            JsonObject rootJson = GsonHelper.parse(reader);
            CultureFileHelper helper = new CultureFileHelper(detectedId, CULTURE_JSON_FILE_NAME, jsonFileLocation, "culture");
            /* Reading mandatory id to avoid conflicts with other cultures */
            if (!helper.getString(rootJson, "id").equals(detectedId)) {
                helper.throwInvalidField("id", "It should match the name of the data pack culture's folder." );
            }
            /* Reading Specializations */
            List<Specialization> specializations = readSpecializations(rootJson, helper);
            /* Reading Eras */
            List<Era> eras = readEras(rootJson, helper);
            /* Reading BuildTypes */
            HashMap<String, BuildType> buildTypeMap = new HashMap<>();
            /* Reading Buildings */
            var buildingsRls = resourceManager.listResources(CULTURE_FOLDER_NAME + "/" + detectedId + "/buildings", (rl) -> rl.getPath().endsWith(".json")).keySet();
            for (ResourceLocation buildingRL : buildingsRls) {
               String rlPath = buildingRL.getPath();
               String typeId = rlPath.substring(rlPath.lastIndexOf('/') + 1, rlPath.lastIndexOf('.'));
               if (buildTypeMap.containsKey(typeId)) {
                   throw new CorruptedCultureException(detectedId, "Duplicated building type '" + typeId + "'.");
               }
               buildTypeMap.put(typeId, BuildingType.createFromDataPack(detectedId, buildingRL, resourceManager));
           }
            /* Reading Roads */
            var roadsRls = resourceManager.listResources(CULTURE_FOLDER_NAME + "/" + detectedId + "/roads", (rl) -> rl.getPath().endsWith(".json")).keySet();
            for (ResourceLocation roadRl : roadsRls) {
                String rlPath = roadRl.getPath();
                String typeId = rlPath.substring(rlPath.lastIndexOf('/') + 1, rlPath.lastIndexOf('.'));
                if (buildTypeMap.containsKey(typeId)) {
                    throw new CorruptedCultureException(detectedId, "Duplicated building type '" + typeId + "'.");
                }
                buildTypeMap.put(typeId, RoadType.createFromDataPack(detectedId, roadRl, resourceManager));
            }
            /* Reading starter pack */
            JsonArray packArray = helper.getJsonArray(rootJson, "buildings_starter_pack");
            HashMap<String, Pair<Integer, Integer>> starterPack = new HashMap<>();
            String loc = "in buildings_starter_pack[]";
            for (JsonElement je : packArray) {
                JsonObject elemJson = helper.asJsonObject(je, "buildings_starter_pack[] element", loc);
                String buildingTypeId = helper.getString(elemJson, "id", loc);
                if (starterPack.containsKey(buildingTypeId)) {
                    helper.throwInvalidField("id", loc, "Duplicated building type id '" + buildingTypeId + "' in the starter pack.");
                }
                if (!buildTypeMap.containsKey(buildingTypeId)){
                    helper.throwInvalidField("id", "Unknown building type id '" + buildingTypeId + "' in the starter pack. Maybe a typo ?");
                }
                int min = helper.getPositiveInt(elemJson, "min", loc);
                int max = helper.getPositiveInt(elemJson, "max", loc);
                if (max < min) {
                    helper.throwInvalidField("max", loc, "It should be >= than 'min' field.");
                }
                starterPack.put(buildingTypeId, new Pair<>(min, max));
            }
            if (starterPack.isEmpty()) {
                helper.throwInvalidField("buildings_starter_pack", "It can't be empty. Each town should have at least one building when spawning.");
            }
            /* Finished reading all the culture's files */
            Ouat.info("Culture '" + detectedId + "' loaded.");
            return new Culture(detectedId, specializations, buildTypeMap, starterPack, eras);
        } catch (IOException ioException) {
            throw new CorruptedCultureException(detectedId, "Could not open the culture json file.");
        } catch (CorruptedCultureException cce) {
            Ouat.error(cce.getMessage());
            // For debug throw cce;
            return null;
        }
    }

    private static List<Specialization> readSpecializations(JsonObject rootJson, CultureFileHelper helper) throws CorruptedCultureException {
        List<Specialization> specializations = new ArrayList<>();
        JsonArray array = helper.getJsonArray(rootJson,"specializations");
        Set<String> ids = new HashSet<>();
        String loc = "in specializations[]";
        for (JsonElement je : array) {
            String id = helper.getString(helper.asJsonObject(je, "specializations[] element", loc), "id", loc);
            if (id.isBlank()) {
                helper.throwInvalidField("id", loc, "Blank string detected !");
            }
            if (!ids.add(id)) {
                helper.throwInvalidField("id", loc, "Duplicated id '" + id + "'.");
            }
            ids.add(id);
            specializations.add(new Specialization(id));
        }
        if (specializations.isEmpty()) {
            helper.throwInvalidField("specializations", "It can't be empty. Each culture should have at least one specialization.");
        }
        return specializations;
    }

    private static List<Era> readEras(JsonObject rootJson, CultureFileHelper helper) throws CorruptedCultureException {
        List<Era> eras = new ArrayList<>();
        JsonArray array = helper.getJsonArray(rootJson,"eras");
        String loc = "in eras[]";
        int i = 1;
        for (JsonElement je : array) {
            JsonObject elemJson = helper.asJsonObject(je, "eras[] element", loc);
            int requiredExperience = helper.getPositiveInt(elemJson, "required_experience", loc);
            int maxBuildingsWeight = helper.getPositiveInt(elemJson, "max_buildings_weight", loc);
            // TODO verify that required_experience and max_buildings_weight is ascending
            eras.add(new Era(i, requiredExperience, maxBuildingsWeight));
            ++i;
        }
        if (eras.isEmpty()) {
            helper.throwInvalidField("eras", "It can't be empty. Each culture should have at least one era.");
        }
        return eras;
    }

    public BuildType getBuildType(String typeId) {
        //TODO return default build type in case of invalid parameter
        return buildTypes.get(typeId);
    }

    /**
     * Returns a random list of buildings that should spawn in a naturally generated hamlet.
     * @param rand RandomSource used to roll the number of each BuildingType.
     * @return The list of BuildingTypes to build.
     */
    public List<BuildingType> getRandomStarterPack(RandomSource rand) {
        List<BuildingType> types = new ArrayList<>();
        for (String typeId : starterPack.keySet()) {
            Pair<Integer, Integer> minMax = starterPack.get(typeId);
            BuildType buildType = buildTypes.get(typeId);
            if (buildType instanceof BuildingType buildingType) {
                int times = rand.nextIntBetweenInclusive(minMax.getA(), minMax.getB());
                for (int i = 0; i < times; ++i) {
                    types.add(buildingType);
                }
            } else {
                // No throw but potentially wrongly generated town
                Ouat.error(new CorruptedCultureException(id, "The buildings starter pack contains an invalid building type : '%s'.".formatted(typeId)).getMessage());
            }
        }
        if (types.isEmpty()) {
            // No throw but potentially wrongly generated town
            Ouat.error(new CorruptedCultureException(id, "This culture's staterpack is empty.").getMessage());
        }
        Collections.shuffle(types); // So that the builds do not always spawn in the same sequence
        return types;
    }

    public List<Era> getEras() {
        return eras;
    }

    public List<Specialization> getSpecializations() {
        return specializations;
    }

    public HashMap<String, Pair<Integer, Integer>> getStarterPack() {
        return starterPack;
    }

    public String getId() {
        return id;
    }

    public List<BuildType> getBuildTypes() {
        return buildTypes.values().stream().toList();
    }
}
