package com.dotteam.onceuponatown.culture;

import com.dotteam.onceuponatown.util.OuatLog;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CultureManager implements PreparableReloadListener {
    private static CultureManager instance;
    private Map<String, Culture> cultures = new HashMap<>();

    public static CultureManager instance() {
        return instance == null ? new CultureManager() : instance;
    }

    public Culture getCulture(String name) {
        return this.cultures.get(name);
    }

    public void loadCultures(ResourceManager manager) {
        var detectedCultures = new HashMap<>(manager.listResources("cultures",
                (rl) -> rl.getPath().endsWith("/ouat_culture.json")));
        OuatLog.info(detectedCultures.size() + " detected cultures will be loaded");
        detectedCultures.forEach((rl, res) -> loadCulture(rl, res, manager));
    }

    private void loadCulture(ResourceLocation mainJsonLocation, Resource mainJsonResource, ResourceManager manager) {
        OuatLog.info("Loading culture \"" + mainJsonLocation.getPath() + "\"");
        JsonObject cultureJsonObject;
        try (Reader reader = mainJsonResource.openAsReader() ){
            cultureJsonObject = GsonHelper.parse(reader);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Culture culture = buildCulture(cultureJsonObject);
        if (!mainJsonLocation.getPath().equals("cultures/" + culture.getId() + "/ouat_culture.json")) {
            throw new RuntimeException("Once upon a town : corrupted culture " + culture.getId() + " : wrong json file location");
        }
        String cultureNamespace = mainJsonLocation.getNamespace();

        var buildingJsons = manager.listResources("cultures/" + culture.getId() + "/buildings",
                    (resourceLocation) -> resourceLocation.getNamespace().equals(cultureNamespace) && resourceLocation.getPath().endsWith(".json")).keySet();
            buildingJsons.forEach((buildingJson) -> {
                BuildingType buildingType = readBuildingJson(buildingJson, manager);
                culture.addBuildingType(buildingType);
            });

    }

    private Culture buildCulture(JsonObject cultureJson) {
        String file = "ouat_culture.json";
        String cultureId = cultureJson.get("id").getAsString();
        // CULTURE ID
        if ((cultureId == null)) {
            //throw new CorruptedCultureException(cultureJson.getPath(), file, "id");
        }
        // MINIMUM AMOUNT OF SPAWNED BUILDINGS
        int starterPackMinSize = cultureJson.get("starter_pack_min_size").getAsInt();
        if (starterPackMinSize < 1) {
            throw new CorruptedCultureException(cultureId, file, "starter_pack_min_size");
        }
        // MAXIMUM AMOUNT OF SPAWNED BUILDINGS
        int starterPackMaxSize = cultureJson.get("starter_pack_max_size").getAsInt();
        if (starterPackMaxSize < 1 || starterPackMaxSize < starterPackMinSize) {
            throw new CorruptedCultureException(cultureId, file, "starter_pack_max_size");
        }
        // ALLOWED FOODS
        List<Item> foodsList = new ArrayList<>();
        var foods = cultureJson.getAsJsonArray("foods");
        if (foods == null) {
            throw new CorruptedCultureException(cultureId, file, "foods");
        }
        foods.forEach((jsonElement -> {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(jsonElement.getAsString()));
            foodsList.add(item);
        }));
        Culture culture = new Culture(cultureId, starterPackMinSize, starterPackMaxSize, foodsList);
        // ERAS
        List<Culture.Era> erasList = new ArrayList<>();
        var eras = cultureJson.getAsJsonArray("eras");
        if (eras == null) {
            throw new CorruptedCultureException(cultureId, file, "eras");
        }
        eras.forEach((jsonElement -> {
            var jsonObject = jsonElement.getAsJsonObject();
            int order = jsonObject.get("order").getAsInt();
            int requiredXp = jsonObject.get("required_xp").getAsInt();
            int buildingsWeight = jsonObject.get("buildings_weight").getAsInt();
            erasList.add(new Culture.Era(order, requiredXp, buildingsWeight));
        }));
        // ORIENTATIONS (without building list)
        List<Orientation> orientationsList = new ArrayList<>();
        var orientations = cultureJson.getAsJsonArray("orientations");
        if (orientations == null) {
            throw new CorruptedCultureException(cultureId, file, "eras");
        }
        orientations.forEach((jsonElement -> {
            var jsonObject = jsonElement.getAsJsonObject();
            String id = jsonObject.get("id").getAsString();
            orientationsList.add(new Orientation(id));
        }));

        erasList.forEach((culture::addEra));
        orientationsList.forEach(culture::addOrientation);
        return culture;
    }

    private BuildingType readBuildingJson(ResourceLocation buildingJson, ResourceManager manager) {
        return null;
    }

    private CitizenJob readJobJson(ResourceLocation jobJson, ResourceManager manager) {
        return null;
    }

    public CompletableFuture<Void> reload(PreparationBarrier stage, ResourceManager resourceManager, ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler, Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {

        }, backgroundExecutor)).thenCompose(stage::wait);
    }
}
