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
import org.dawnoftime.onceuponatown.town.ProductionEntry;
import org.dawnoftime.onceuponatown.town.TransformationRecipe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BuildingDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuildingDataHandler.class);
    private static final Map<String, BuildingDef> REGISTRY = new HashMap<>();

    // Called from OuatFabric via ServerLifecycleEvents.SERVER_STARTING
    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("buildings", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    BuildingDef def = parseDef(json);
                    REGISTRY.put(def.id, def);
                    LOGGER.debug("[OUAT] Loaded building def: {}", def.id);
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load building def {}: {}", location, e.getMessage());
                }
            });
        LOGGER.info("[OUAT] Loaded {} building definitions: {}", REGISTRY.size(), REGISTRY.keySet());
    }

    private static BuildingDef parseDef(JsonObject json) {
        String id = json.get("id").getAsString();
        ResourceLocation nbt = new ResourceLocation(json.get("nbt").getAsString());

        List<ProductionEntry> production = new ArrayList<>();
        if (json.has("production")) {
            for (JsonElement el : json.getAsJsonArray("production")) {
                JsonObject p = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(p.get("item").getAsString()));
                production.add(new ProductionEntry(
                    item,
                    p.get("amount").getAsInt(),
                    p.get("every_ticks").getAsInt(),
                    p.get("capacity_stacks").getAsInt()
                ));
            }
        }

        List<ItemCost> costs = new ArrayList<>();
        if (json.has("construction_cost")) {
            for (JsonElement el : json.getAsJsonArray("construction_cost")) {
                JsonObject c = el.getAsJsonObject();
                Item item = BuiltInRegistries.ITEM.get(new ResourceLocation(c.get("item").getAsString()));
                costs.add(new ItemCost(item, c.get("amount").getAsInt()));
            }
        }

        String entryPool = json.has("entry_pool") ? json.get("entry_pool").getAsString() : "";
        boolean terrainMatching = json.has("terrain_matching") && json.get("terrain_matching").getAsBoolean();
        String iconItem = json.has("icon_item") ? json.get("icon_item").getAsString() : "minecraft:dirt";
        String category = json.has("category") ? json.get("category").getAsString() : "buildings";

        List<String> footprint = null;
        if (json.has("footprint")) {
            footprint = new ArrayList<>();
            for (JsonElement el : json.getAsJsonArray("footprint")) {
                footprint.add(el.getAsString());
            }
        }

        List<TransformationRecipe> transformations = new ArrayList<>();
        if (json.has("transformations")) {
            for (JsonElement el : json.getAsJsonArray("transformations")) {
                JsonObject t = el.getAsJsonObject();
                List<ItemCost> inputs = new ArrayList<>();
                for (JsonElement inputEl : t.getAsJsonArray("inputs")) {
                    JsonObject inp = inputEl.getAsJsonObject();
                    Item inputItem = BuiltInRegistries.ITEM.get(new ResourceLocation(inp.get("item").getAsString()));
                    inputs.add(new ItemCost(inputItem, inp.get("amount").getAsInt()));
                }
                Item outputItem = BuiltInRegistries.ITEM.get(new ResourceLocation(t.get("output").getAsString()));
                transformations.add(new TransformationRecipe(
                    inputs,
                    outputItem,
                    t.get("output_amount").getAsInt(),
                    t.get("output_capacity_stacks").getAsInt()
                ));
            }
        }
        float transformInputRatio = json.has("transform_input_ratio") ? json.get("transform_input_ratio").getAsFloat() : 0.1f;
        int transformEveryTicks = json.has("transform_every_ticks") ? json.get("transform_every_ticks").getAsInt() : 1600;

        String orientation = json.has("orientation") ? json.get("orientation").getAsString() : "";
        List<String> bootstrapCandidates = new ArrayList<>();
        if (json.has("bootstrapCandidates")) {
            for (JsonElement el : json.getAsJsonArray("bootstrapCandidates")) {
                bootstrapCandidates.add(el.getAsString());
            }
        }

        double productionBonus = json.has("production_bonus") ? json.get("production_bonus").getAsDouble() : 0.0;

        return new BuildingDef(id, nbt, entryPool, production, costs, terrainMatching, iconItem, category, footprint,
            transformations, transformInputRatio, transformEveryTicks, orientation, bootstrapCandidates, productionBonus);
    }

    public static Optional<BuildingDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<BuildingDef> getAll() { return REGISTRY.values(); }
}
