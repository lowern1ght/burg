package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.town.QuestDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuestDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestDataHandler.class);
    private static final Map<String, QuestDef> REGISTRY = new HashMap<>();

    public static void reload(MinecraftServer server) {
        REGISTRY.clear();
        ResourceManager rm = server.getResourceManager();
        rm.listResources("quests", path -> path.getPath().endsWith(".json"))
            .forEach((location, resource) -> {
                if (!location.getNamespace().equals(Ouat.MOD_ID)) return;
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    QuestDef def = parseDef(json);
                    REGISTRY.put(def.id(), def);
                } catch (Exception e) {
                    LOGGER.error("[OUAT] Failed to load quest def {}: {}", location, e.getMessage());
                }
            });
        LOGGER.info("[OUAT] Loaded {} quest definitions: {}", REGISTRY.size(), REGISTRY.keySet());
    }

    private static QuestDef parseDef(JsonObject json) {
        String id = json.get("id").getAsString();
        String type = json.has("type") ? json.get("type").getAsString() : "TASK";
        String icon = json.get("icon").getAsString();
        String titleKey = json.get("title").getAsString();
        String descKey = json.get("description").getAsString();
        int spawnWeight = json.has("spawn_weight") ? json.get("spawn_weight").getAsInt() : 10;
        if (!json.has("duration_ticks")) {
            throw new IllegalArgumentException("Quest '" + id + "' is missing required field 'duration_ticks'");
        }
        long durationTicks = json.get("duration_ticks").getAsLong();

        List<QuestDef.ConditionTemplate> conditions = new ArrayList<>();
        if (json.has("conditions")) {
            for (JsonElement el : json.getAsJsonArray("conditions")) {
                JsonObject c = el.getAsJsonObject();
                conditions.add(new QuestDef.ConditionTemplate(
                    c.get("type").getAsString(),
                    c.has("item") ? c.get("item").getAsString() : null,
                    c.has("required") ? c.get("required").getAsInt() : 0
                ));
            }
        }

        QuestDef.RewardTemplate reward = null;
        if (json.has("reward")) {
            JsonObject r = json.getAsJsonObject("reward");
            reward = new QuestDef.RewardTemplate(
                r.get("type").getAsString(),
                r.has("item") ? r.get("item").getAsString() : null,
                r.has("amount") ? r.get("amount").getAsInt() : 0
            );
        }

        return new QuestDef(id, type, icon, titleKey, descKey, conditions, reward, spawnWeight, durationTicks);
    }

    public static Optional<QuestDef> get(String id) { return Optional.ofNullable(REGISTRY.get(id)); }
    public static Collection<QuestDef> getAll() { return REGISTRY.values(); }
}
