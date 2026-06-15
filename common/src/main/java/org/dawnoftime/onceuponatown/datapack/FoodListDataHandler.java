package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Ouat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Loads food_list.json from the config datapack folder.
// Insertion order is preserved and defines drain priority (lower fuv items drain first).
public class FoodListDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(FoodListDataHandler.class);

    private static Map<Item, Integer> FOOD_MAP = Collections.emptyMap();

    public static void reload(MinecraftServer server) {
        LinkedHashMap<Item, Integer> newMap = new LinkedHashMap<>();
        ResourceManager rm = server.getResourceManager();
        var resources = rm.listResources("config", path -> path.getPath().endsWith("food_list.json"));
        for (var entry : resources.entrySet()) {
            if (!entry.getKey().getNamespace().equals(Ouat.MOD_ID)) continue;
            try (InputStreamReader reader = new InputStreamReader(entry.getValue().open())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json.has("food_list")) {
                    JsonArray array = json.getAsJsonArray("food_list");
                    for (var el : array) {
                        JsonObject obj = el.getAsJsonObject();
                        String itemId = obj.get("item").getAsString();
                        int fuv = obj.get("fuv").getAsInt();
                        ResourceLocation rl = new ResourceLocation(itemId);
                        Item item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null);
                        if (item == null) {
                            LOGGER.warn("[OUAT] food_list.json: unknown item '{}', skipping", itemId);
                            continue;
                        }
                        newMap.put(item, fuv);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("[OUAT] Failed to load food_list.json: {}", e.getMessage());
            }
            break;
        }
        FOOD_MAP = Collections.unmodifiableMap(newMap);
        LOGGER.info("[OUAT] Loaded food list: {} entries", FOOD_MAP.size());
    }

    public static int getFuv(Item item) {
        return FOOD_MAP.getOrDefault(item, 0);
    }

    public static Set<Map.Entry<Item, Integer>> entriesInOrder() {
        return FOOD_MAP.entrySet();
    }
}
