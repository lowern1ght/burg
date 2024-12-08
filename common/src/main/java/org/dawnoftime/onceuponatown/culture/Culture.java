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

import static org.dawnoftime.onceuponatown.culture.CultureManager.CULTURE_FILE;

// TODO Add some code to manage when a map was saved with a different version of the culture datapack (ie. a BuildType was removed).
public class Culture {
    public static final String ROAD_TYPE_NAME = "road";
    public static final String WIDE_ROAD_TYPE_NAME = "wide_road";
    public static final String BRIDGE_TYPE_NAME = "bridge";
    public static final String WALL_TYPE_NAME = "wall";

    private final String id;
    private List<Orientation> orientations;
    private final HashMap<String, BuildType> buildTypeMap = new HashMap<>();
    private final HashMap<String, Pair<Integer, Integer>> starterPack = new HashMap<>();
    private List<Item> foods;
    private final List<Era> eras;

    private Culture(String id, List<Era> eras) {
        this.id = id;
        this.eras = eras;
    }

    public List<Era> getEras() {
        return this.eras;
    }

    public String getId() {
        return this.id;
    }

    /**
     * Returns a list that contains all the BuildTypes that should be built in a random order.
     * @param rand RandomSource used to roll the number of each BuildType.
     * @return The list of BuildType to build.
     */
    public List<BuildingType> getRandomStarterPack(RandomSource rand) {
        List<BuildingType> types = new ArrayList<>();
        for (String buildTypeName: this.starterPack.keySet()){
            Pair<Integer, Integer> range = this.starterPack.get(buildTypeName);
            for (int n = range.getA(); n < rand.nextIntBetweenInclusive(range.getA(), range.getB()); n++){
                BuildType type = this.buildTypeMap.get(buildTypeName);
                if(type instanceof BuildingType buildingType){
                    types.add(buildingType);
                }else{
                    Ouat.error(new CorruptedCultureException(this.id, "A build '%s' listed in the starter pack is not a building from this culture's datapack.".formatted(buildTypeName)).getMessage());
                }
            }
        }
        Collections.shuffle(types);
        return types;
    }

    public static @Nullable Culture createCulture(String cultureId, ResourceLocation fileLocation, Resource cultureJsonResource, ResourceManager resourceManager) {
        Ouat.info("Loading culture '" + cultureId + "'...");
        JsonObject cultureJsonObject;
        try (Reader reader = cultureJsonResource.openAsReader()){
            cultureJsonObject = GsonHelper.parse(reader);

            // Eras
            List<Culture.Era> eras = readEras(cultureJsonObject, cultureId);

            // Orientations Ids
            List<String> orientationsIds = readOrientationsIds(cultureJsonObject, cultureId);

            Culture culture = new Culture(cultureId, eras);

            // Mandatory BuildType
            culture.addBuildType(new SliceBuildType(ROAD_TYPE_NAME));
            culture.addBuildType(new SliceBuildType(WIDE_ROAD_TYPE_NAME));
            //culture.addBuildType(new SliceBuildType(BRIDGE_TYPE_NAME));
            //culture.addBuildType(new SliceBuildType(WALL_TYPE_NAME));

            // BuildType
            var buildResources = resourceManager.listResources("cultures/" + cultureId + "/builds/build_type", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                BuildType buildingType = BuildingType.createFromJson(resourceManager, buildResource, cultureId);
                if(buildingType != null){
                    culture.addBuildType(buildingType);
                }
            });

            // BuildVariant
            buildResources = resourceManager.listResources("cultures/" + cultureId + "/builds/build_variant", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                Triplet<String, BuildVariant, String> variant = BuildVariant.createFromJson(resourceManager, buildResource, cultureId);
                if(variant != null){
                    culture.addBuildVariant(variant.getA(), variant.getB(), variant.getC());
                }
            });

            // Now we remove the BuildType that don't have any variant since they can't be built.
            culture.dropBuildTypeWithoutVariant();

            // Load the starter pack
            JsonElement elem = CultureManager.tryGet(cultureJsonObject, "starter_pack", CULTURE_FILE, cultureId, CULTURE_FILE, fileLocation);
            JsonArray array = elem.getAsJsonArray();
            for(JsonElement arrayElem : array){
                JsonObject subObject = arrayElem.getAsJsonObject();
                String name = CultureManager.tryGet(subObject, "build_type", " in an object in the section 'starter_pack'", CULTURE_FILE, cultureId, CULTURE_FILE, fileLocation).getAsString();
                if(!culture.buildTypeMap.containsKey(name)){
                    throw new CorruptedCultureException(cultureId, "Failed to load a culture. The build_type '%s' in the starter pack is unknown, please check this file: %s".formatted(name, CULTURE_FILE));
                }
                int min = CultureManager.tryGet(subObject, "min", " in an object in the section 'starter_pack'", CULTURE_FILE, cultureId, CULTURE_FILE, fileLocation).getAsInt();
                int max = CultureManager.tryGet(subObject, "max", " in an object in the section 'starter_pack'", CULTURE_FILE, cultureId, CULTURE_FILE, fileLocation).getAsInt();
                if(min < 1 || max < min){
                    throw new CorruptedCultureException(cultureId, "Failed to load a culture. Check the values of the minimum and maximum number of the build_type '%s' in the starter pack in this file: %s".formatted(name, fileLocation));
                };
                culture.addStarterPackBuild(name, min, max);
            }
            return culture;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (CorruptedCultureException e){
            Ouat.error(e.getMessage());
            return null;
        }
    }

    private void addOrientation(Orientation orientation) {
        this.orientations.add(orientation);
    }

    private void addEra(Era era) {
        this.eras.add(era);
    }

    private void addBuildType(@NotNull BuildType type) {
        this.buildTypeMap.put(type.getName(), type);
    }

    private void addBuildVariant(@NotNull String buildTypeName, @NotNull BuildVariant variant, @NotNull String shape){
        BuildType type = this.buildTypeMap.get(buildTypeName);
        if(type != null){
            type.addVariant(variant, shape, this.id);
        }else{
            Ouat.error("Culture [%s]: Failed to register the build_variant '%s'. Its associated build_type '%s' is not defined for this culture.".formatted(this.id, variant.getName(), buildTypeName));
        }
    }

    private void addStarterPackBuild(String buildTypeName, int min, int max){
        this.starterPack.put(buildTypeName, new Pair<>(min, max));
    }

    public BuildType getBuildType(String buildName){
        return this.buildTypeMap.get(buildName);
    }

    private void dropBuildTypeWithoutVariant(){
        this.buildTypeMap.entrySet().removeIf(entry -> entry.getValue().isNotValid(this.id));
    }

    private static List<Culture.Era> readEras(JsonObject cultureJsonObject, String cultureId) throws CorruptedCultureException {
        List<Culture.Era> eras = new ArrayList<>();
        var eraArray = cultureJsonObject.getAsJsonArray("eras");
        if (eraArray == null) { // Error : no eras
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "eras", "Missing eras definition");
        }
        int eraIndex = 1;
        for (int i = 0; i < eraArray.size(); ++i) {
            var jsonObject = eraArray.get(i).getAsJsonObject();
            int requiredXp = jsonObject.get("required_xp").getAsInt();
            if (requiredXp < 0) { // Error : required xp can not be negative
                throw new CorruptedCultureException(cultureId, CULTURE_FILE, "era requiredXp", "Era required xp can not be negative");
            }
            int buildingsWeight = jsonObject.get("buildings_weight").getAsInt();
            if (buildingsWeight <= 0) { // Error : buildings weight can not be null or negative
                throw new CorruptedCultureException(cultureId, CULTURE_FILE, "era buildings_weight", "buildings_weight can not be negative");
            }
            eras.add(new Culture.Era(eraIndex, requiredXp, buildingsWeight));
            ++eraIndex;
        }
        return eras;
    }

    private static List<Item> readFoodList(JsonObject cultureJsonObject) throws CorruptedCultureException {
        // Foods that the npc are allowed to eat
        /*
        List<Item> foodList = new ArrayList<>();
        var foods = cultureJson.getAsJsonArray("foods");
        if (foods == null) {
            throw new CorruptedCultureException(cultureId, file, "foods");
        }
        foods.forEach((jsonElement -> {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(jsonElement.getAsString()));
            foodList.add(item);
        }));
         */
        return null;
    }

    private static List<String> readOrientationsIds(JsonObject cultureJsonObject, String cultureId) throws CorruptedCultureException {
        List<String> orientationsIds = new ArrayList<>();
        var orientations = cultureJsonObject.getAsJsonArray("orientations");
        if (orientations == null) { // Error : no orientations
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "orientations", "Missing town orientations definition");
        }
        orientations.forEach((jsonElement -> {
            String orientationId = getString(cultureId, jsonElement);
            orientationsIds.forEach((id) -> {
                if (id.equals(orientationId)) { // Error : duplicate orientation id
                    throw new CorruptedCultureException(cultureId, CULTURE_FILE, "orientations", "Multiple orientations share the same id");
                }
            });
            orientationsIds.add(orientationId);
        }));
        return orientationsIds;
    }

    @NotNull
    private static String getString(String cultureId, JsonElement jsonElement) throws CorruptedCultureException {
        var jsonObject = jsonElement.getAsJsonObject();
        String orientationId = jsonObject.get("id").getAsString();
        if (orientationId == null ) { // Error : missing orientation id
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "orientations", "Missing an orientation id");
        }
        if (orientationId.isBlank()) { // Error : invalid orientation id
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "orientations", "Invalid orientation id");
        }
        return orientationId;
    }

    public record Era(int order, int requiredXp, int buildingsWeight) {}
}
