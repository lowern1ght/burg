package org.dawnoftime.onceuponatown.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.Optional;

public class QuestConfigDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestConfigDataHandler.class);

    public static final class Config {
        public final int generationIntervalTicks;
        public final int expiryCheckIntervalTicks;

        public Config(int generationIntervalTicks, int expiryCheckIntervalTicks) {
            this.generationIntervalTicks = generationIntervalTicks;
            this.expiryCheckIntervalTicks = expiryCheckIntervalTicks;
        }
    }

    private static final Config DEFAULTS = new Config(1200, 100);
    private static Config loaded = DEFAULTS;

    public static Config get() { return loaded; }

    public static void reload(MinecraftServer server) {
        ResourceLocation location = new ResourceLocation(Ouat.MOD_ID, "config/quest_config.json");
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            LOGGER.warn("[OUAT] Quest config not found at {} -- using defaults", location);
            loaded = DEFAULTS;
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            loaded = new Config(
                getInt(json, "generation_interval_ticks",   DEFAULTS.generationIntervalTicks),
                getInt(json, "expiry_check_interval_ticks", DEFAULTS.expiryCheckIntervalTicks)
            );
            LOGGER.info("[OUAT] Quest config loaded from {}", location);
        } catch (Exception e) {
            LOGGER.error("[OUAT] Failed to load quest config {}: {} -- using defaults", location, e.getMessage());
            loaded = DEFAULTS;
        }
    }

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }
}
