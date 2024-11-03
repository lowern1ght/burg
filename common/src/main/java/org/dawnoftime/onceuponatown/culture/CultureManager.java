package org.dawnoftime.onceuponatown.culture;

import com.google.gson.JsonElement;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.building.type.BuildingType;
import org.dawnoftime.onceuponatown.town.building.type.NpcJob;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CultureManager implements PreparableReloadListener {
    public static final String CULTURE_FILE = "ouat_culture.json";
    private static final Map<String, Culture> loadedCultures = new HashMap<>();

    public static List<Culture> getLoadedCultures() {
        return loadedCultures.values().stream().toList();
    }

    public static Culture getCultureById(String cultureId) {
        return loadedCultures.get(cultureId);
    }

    public static void loadCultures(ResourceManager manager) {
        loadedCultures.clear();
        var detectedCultures = new HashMap<>(manager.listResources("cultures", (rl) -> rl.getPath().endsWith("/ouat_culture.json")));
        Ouat.OuatLog.info(detectedCultures.size() + " detected cultures will be loaded");
        detectedCultures.forEach((rl, res) -> {
            Culture culture = createCulture(rl, res, manager);
            loadedCultures.put(culture.getId(), culture);
        });
        Ouat.OuatLog.info("Loaded " + loadedCultures.size() + " culture(s)");
    }

    private static String readCultureId(JsonObject cultureJsonObject, ResourceLocation cultureJsonResourceLocation) {
        String cultureId = cultureJsonObject.get("id").getAsString();
        if ((cultureId == null)) { // Error : missing culture
            throw new CorruptedCultureException("(?)", CULTURE_FILE, "id", "Missing culture id");
        }
        if (cultureId.isBlank()) { // Error : invalid culture id
            throw new CorruptedCultureException("(?)", CULTURE_FILE, "id", "Invalid culture id");
        }
        if (loadedCultures.containsKey(cultureId)) { // Error : id duplicate
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "id", "Multiple cultures share the same id");
        }
        if (!cultureJsonResourceLocation.getPath().equals("cultures/" + cultureId + "/ouat_culture.json")) { // Error : wrong culture folder name in datapack
            throw new CorruptedCultureException(cultureId, "Wrong culture folder name in datapack");
        }
        return cultureId;
    }

    private static List<Culture.Era> readEras(JsonObject cultureJsonObject, String cultureId) {
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

    private static List<Item> readFoodList(JsonObject cultureJsonObject) {
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

    private static List<String> readOrientationsIds(JsonObject cultureJsonObject, String cultureId) {
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
    private static String getString(String cultureId, JsonElement jsonElement) {
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

    private static Culture createCulture(ResourceLocation cultureJsonResourceLocation, Resource cultureJsonResource, ResourceManager resourceManager) {
        Ouat.OuatLog.info("Loading culture \"" + cultureJsonResourceLocation.getPath() + "\"");
        JsonObject cultureJsonObject;
        try (Reader reader = cultureJsonResource.openAsReader()){
            cultureJsonObject = GsonHelper.parse(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Culture ID
        String cultureId = readCultureId(cultureJsonObject, cultureJsonResourceLocation);

        // Minimum amount of initial buildings
        int starterPackMinSize = cultureJsonObject.get("starter_pack_min_size").getAsInt();
        if (starterPackMinSize <= 0) { // Error : less than 1 building in starter pack
            throw new CorruptedCultureException(cultureId, CULTURE_FILE, "starter_pack_min_size", "Town starterpack should have at least one building. Detected value is <= 0.");
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
        //orientationsIds.forEach(System.out::println);
        
        
        /*
        String cultureNamespace = cultureJsonResourceLocation.getNamespace();
        var buildingJsons = manager.listResources("cultures/" + culture.getId() + "/buildings",
                    (resourceLocation) -> resourceLocation.getNamespace().equals(cultureNamespace) && resourceLocation.getPath().endsWith(".json")).keySet();
            buildingJsons.forEach((buildingJson) -> {
                BuildingType buildingType = readBuildingJson(buildingJson, manager);
                culture.addBuildingType(buildingType);
            });

         */
        return new Culture(cultureId, starterPackMinSize, starterPackMaxSize, eras);
    }

    private BuildingType readBuildingJson(ResourceLocation buildingJson, ResourceManager manager) {
        return null;
    }

    private NpcJob readJobJson(ResourceLocation jobJson, ResourceManager manager) {
        return null;
    }

    public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
        }, backgroundExecutor)).thenCompose(stage::wait);
    }
}
