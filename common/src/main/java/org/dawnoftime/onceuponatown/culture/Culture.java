package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;
import oshi.util.tuples.Triplet;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

import static org.dawnoftime.onceuponatown.culture.CultureManager.CULTURE_FILE;

// TODO Gérer les cas où une map a été sauvegarder avec une version différente du datapack (par exemple un buildType qui a disparu).

public class Culture {
    public static final Culture FAKE_PLAINS = new Culture("fake_plains", 3, 14, null);
    private final String id;
    private List<Orientation> orientations;
    private final HashMap<String, BuildType> buildTypeMap = new HashMap<>();
    private final int starterPackMinSize;
    private final int starterPackMaxSize;
    // Starter pack candidate, building type id, min amount, max amount
    private Triplet<BuildType, Integer, Integer> starterPack;
    private List<Item> foods;
    private final List<Era> eras;

    Culture(String id, int starterPackMinSize, int starterPackMaxSize, List<Era> eras) {
        this.id = id;
        this.starterPackMinSize = starterPackMinSize;
        this.starterPackMaxSize = starterPackMaxSize;
        this.eras = eras;
    }

    public List<BuildType> getRandomStarterPack() {
        // TODO Replace this with datapack info later !
        /*
        List<TownGeneratorOld.BuildingInfo> availableBuildings = new LinkedList<>(Arrays.asList(TEST_BUILDINGS));
        List<TownGeneratorOld.BuildingInfo> starterPack = new ArrayList<>();
        for (int i = 0; i < STARTER_PACK_SIZE; ++i) {
            TownGeneratorOld.BuildingInfo building = availableBuildings.remove(Mth.nextInt(random, 0, availableBuildings.size() - 1));
            starterPack.add(building);
            if (availableBuildings.isEmpty()) {
                availableBuildings.addAll(Arrays.asList(TEST_BUILDINGS));
            }
        }
        return starterPack;

         */
        return new ArrayList<>();
    }

    public void addOrientation(Orientation orientation) {
        this.orientations.add(orientation);
    }

    public List<Era> getEras() {
        return this.eras;
    }

    public void addEra(Era era) {
        this.eras.add(era);
    }

    public void addBuildType(@NotNull BuildType type) {
        this.buildTypeMap.put(type.getName(), type);
    }

    public void addBuildVariant(@NotNull String buildTypeName, @NotNull BuildVariant variant){
        BuildType type = this.buildTypeMap.get(buildTypeName);
        if(type != null){
            type.addVariant(variant);
        }else{
            Ouat.error("Culture [%s]: Failed to register the build_variant '%s'. Its associated build_type '%s' is not defined for this culture.".formatted(this.id, variant.getName(), buildTypeName));
        }
    }

    public void dropBuildTypeWithoutVariant(){
        this.buildTypeMap.entrySet().removeIf(entry -> {
            if(entry.getValue().getVariantNumber() == 0){
                Ouat.error("Culture [%s]: Canceled the registration of the build_type '%s' as it doesn't have any build_variant.".formatted(this.id, entry.getValue().getName()));
                return true;
            }
            return false;
        });
    }

    public String getId() {
        return this.id;
    }

    public int getStarterPackMinSize() {
        return this.starterPackMinSize;
    }

    public int getStarterPackMaxSize() {
        return this.starterPackMaxSize;
    }

    public static @Nullable Culture createCulture(String cultureId, Resource cultureJsonResource, ResourceManager resourceManager) {
        Ouat.info("Loading culture '" + cultureId + "'...");
        JsonObject cultureJsonObject;
        try (Reader reader = cultureJsonResource.openAsReader()){
            cultureJsonObject = GsonHelper.parse(reader);

            // Minimum amount of initial buildings
            int starterPackMinSize = cultureJsonObject.get("starter_pack_min_size").getAsInt();
            if (starterPackMinSize <= 0) { // Error : less than 1 building in starter pack
                throw new CorruptedCultureException(cultureId, CULTURE_FILE, "starter_pack_min_size", "Town starter_pack should have at least one building. Detected value is <= 0.");
            }

            // Maximum amount of initial buildings
            int starterPackMaxSize = cultureJsonObject.get("starter_pack_max_size").getAsInt();
            if (starterPackMaxSize < starterPackMinSize) {  // Error : starter pack min boundary is greater than max boundary
                throw new CorruptedCultureException(cultureId, CULTURE_FILE, "starter_pack_max_size", "starter_pack_max_size has to be greater than starter_pack_min_size.");
            }

            // Eras
            List<Culture.Era> eras = readEras(cultureJsonObject, cultureId);

            // Orientations Ids
            List<String> orientationsIds = readOrientationsIds(cultureJsonObject, cultureId);

            Culture culture = new Culture(cultureId, starterPackMinSize, starterPackMaxSize, eras);

            // BuildType
            var buildResources = resourceManager.listResources("cultures/" + cultureId + "/builds/build_type", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                BuildType buildingType = BuildType.createFromJson(resourceManager, buildResource, cultureId);
                if(buildingType != null){
                    culture.addBuildType(buildingType);
                }
            });

            // BuildVariant
            buildResources = resourceManager.listResources("cultures/" + cultureId + "/builds/build_variant", (resourceLocation) -> resourceLocation.getPath().endsWith(".json")).keySet();
            buildResources.forEach((buildResource) -> {
                Pair<String, BuildVariant> variant = BuildVariant.createFromJson(resourceManager, buildResource, cultureId);
                if(variant != null){
                    culture.addBuildVariant(variant.getA(), variant.getB());
                }
            });

            // Now we remove the BuildType that don't have any variant since they can't be built.
            culture.dropBuildTypeWithoutVariant();

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
