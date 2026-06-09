package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EraTransitionDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(EraTransitionDataHandler.class);
    private static final Map<String, EraTransitionDef> REGISTRY = new HashMap<>();

    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("era_transitions", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    EraTransitionDef def = parseDef(json);
                    REGISTRY.put(def.id, def);
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load era transition def {}: {}", location, e.getMessage());
                }
            });
        LOGGER.info("[OUAT] Loaded {} era transition definitions: {}", REGISTRY.size(), REGISTRY.keySet());
    }

    private static EraTransitionDef parseDef(JsonObject json) {
        String id = json.get("id").getAsString();
        int fromEra = json.get("from_era").getAsInt();
        String fromOrientation = json.has("from_orientation") ? json.get("from_orientation").getAsString() : "";
        String displayName = json.has("display_name") ? json.get("display_name").getAsString() : id;
        String iconItem = json.has("icon_item") ? json.get("icon_item").getAsString() : "minecraft:dirt";
        int minWeight = json.has("min_weight") ? json.get("min_weight").getAsInt() : 0;
        String nextOrientation = json.has("next_orientation") ? json.get("next_orientation").getAsString() : "";

        List<ItemCost> resourceCost = new ArrayList<>();
        if (json.has("resource_cost")) {
            for (JsonElement el : json.getAsJsonArray("resource_cost")) {
                JsonObject c = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()));
                resourceCost.add(new ItemCost(item, c.get("amount").getAsInt()));
            }
        }

        int requiredResidents = json.has("required_residents") ? json.get("required_residents").getAsInt() : 0;

        List<BuildingDef.BuildingRequirement> requiredBuildings = new ArrayList<>();
        if (json.has("required_buildings")) {
            for (JsonElement el : json.getAsJsonArray("required_buildings")) {
                JsonObject rb = el.getAsJsonObject();
                String reqDefId = rb.get("defId").getAsString();
                int reqCount = rb.has("count") ? rb.get("count").getAsInt() : 1;
                requiredBuildings.add(new BuildingDef.BuildingRequirement(reqDefId, reqCount));
            }
        }

        List<String> unlockedBuildingIds = new ArrayList<>();
        if (json.has("unlocked_building_ids")) {
            for (JsonElement el : json.getAsJsonArray("unlocked_building_ids")) {
                unlockedBuildingIds.add(el.getAsString());
            }
        }

        return new EraTransitionDef(id, fromEra, fromOrientation, displayName, iconItem, minWeight,
            resourceCost, requiredResidents, requiredBuildings, unlockedBuildingIds, nextOrientation);
    }

    // Returns transitions available from the given era and orientation.
    // fromOrientation empty in the def means it matches any orientation.
    public static List<EraTransitionDef> getAvailableTransitions(int fromEra, String currentOrientation) {
        List<EraTransitionDef> result = new ArrayList<>();
        for (EraTransitionDef def : REGISTRY.values()) {
            if (def.fromEra != fromEra) continue;
            if (!def.fromOrientation.isEmpty() && !def.fromOrientation.equals(currentOrientation)) continue;
            result.add(def);
        }
        return result;
    }

    public static Optional<EraTransitionDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<EraTransitionDef> getAll() { return REGISTRY.values(); }
}
