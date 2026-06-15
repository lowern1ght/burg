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

public class BuilderConfigDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuilderConfigDataHandler.class);

    public static final class Config {
        public final int idleWaitTicks;
        public final int autonomousRoadIntervalTicks;
        public final double walkSpeed;
        public final double blockReachDistance;
        public final int blockDelayTicks;
        public final int burstPauseMinTicks;
        public final int burstPauseMaxTicks;
        public final int maxBurstExtraBlocks;
        public final int stuckFallbackTicks;
        public final int movingTimeoutTicks;
        public final int pathRefreshIntervalTicks;
        public final float planReadChance;
        public final int planReadMinTicks;
        public final int planReadMaxTicks;

        public Config(int idleWaitTicks, int autonomousRoadIntervalTicks,
                      double walkSpeed, double blockReachDistance, int blockDelayTicks,
                      int burstPauseMinTicks, int burstPauseMaxTicks, int maxBurstExtraBlocks,
                      int stuckFallbackTicks, int movingTimeoutTicks, int pathRefreshIntervalTicks,
                      float planReadChance, int planReadMinTicks, int planReadMaxTicks) {
            this.idleWaitTicks = idleWaitTicks;
            this.autonomousRoadIntervalTicks = autonomousRoadIntervalTicks;
            this.walkSpeed = walkSpeed;
            this.blockReachDistance = blockReachDistance;
            this.blockDelayTicks = blockDelayTicks;
            this.burstPauseMinTicks = burstPauseMinTicks;
            this.burstPauseMaxTicks = burstPauseMaxTicks;
            this.maxBurstExtraBlocks = maxBurstExtraBlocks;
            this.stuckFallbackTicks = stuckFallbackTicks;
            this.movingTimeoutTicks = movingTimeoutTicks;
            this.pathRefreshIntervalTicks = pathRefreshIntervalTicks;
            this.planReadChance = planReadChance;
            this.planReadMinTicks = planReadMinTicks;
            this.planReadMaxTicks = planReadMaxTicks;
        }
    }

    private static final Config DEFAULTS = new Config(
        200, 1200, 0.6, 6.0, 4, 10, 18, 2, 100, 3600, 20, 0.05f, 15, 35
    );

    private static Config loaded = DEFAULTS;

    public static Config get() { return loaded; }

    public static void reload(MinecraftServer server) {
        ResourceLocation location = new ResourceLocation(Ouat.MOD_ID, "jobs/builder.json");
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            LOGGER.warn("[OUAT] Builder config not found at {} -- using defaults", location);
            loaded = DEFAULTS;
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            loaded = new Config(
                getInt(json, "idle_wait_ticks",                DEFAULTS.idleWaitTicks),
                getInt(json, "autonomous_road_interval_ticks", DEFAULTS.autonomousRoadIntervalTicks),
                getDbl(json, "walk_speed",                     DEFAULTS.walkSpeed),
                getDbl(json, "block_reach_distance",           DEFAULTS.blockReachDistance),
                getInt(json, "block_delay_ticks",              DEFAULTS.blockDelayTicks),
                getInt(json, "burst_pause_min_ticks",          DEFAULTS.burstPauseMinTicks),
                getInt(json, "burst_pause_max_ticks",          DEFAULTS.burstPauseMaxTicks),
                getInt(json, "max_burst_extra_blocks",         DEFAULTS.maxBurstExtraBlocks),
                getInt(json, "stuck_fallback_ticks",           DEFAULTS.stuckFallbackTicks),
                getInt(json, "moving_timeout_ticks",           DEFAULTS.movingTimeoutTicks),
                getInt(json, "path_refresh_interval_ticks",    DEFAULTS.pathRefreshIntervalTicks),
                getFlt(json, "plan_read_chance",               DEFAULTS.planReadChance),
                getInt(json, "plan_read_min_ticks",            DEFAULTS.planReadMinTicks),
                getInt(json, "plan_read_max_ticks",            DEFAULTS.planReadMaxTicks)
            );
            LOGGER.info("[OUAT] Builder config loaded from {}", location);
        } catch (Exception e) {
            LOGGER.error("[OUAT] Failed to load builder config {}: {} -- using defaults", location, e.getMessage());
            loaded = DEFAULTS;
        }
    }

    private static int getInt(JsonObject json, String key, int def) {
        return json.has(key) ? json.get(key).getAsInt() : def;
    }

    private static double getDbl(JsonObject json, String key, double def) {
        return json.has(key) ? json.get(key).getAsDouble() : def;
    }

    private static float getFlt(JsonObject json, String key, float def) {
        return json.has(key) ? json.get(key).getAsFloat() : def;
    }
}
