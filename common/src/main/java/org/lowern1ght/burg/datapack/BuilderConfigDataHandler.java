package org.lowern1ght.burg.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.Ouat;
import org.lowern1ght.burg.entity.ai.ActivityDef;
import org.lowern1ght.burg.entity.ai.AnimationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BuilderConfigDataHandler {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(BuilderConfigDataHandler.class);

    public static final class Config {
        public final double walkSpeed;
        public final double blockReachDistance;
        public final int blockDelayTicks;
        public final int burstPauseMinTicks;
        public final int burstPauseMaxTicks;
        public final int maxBurstExtraBlocks;
        public final int stuckFallbackTicks;
        public final int movingTimeoutTicks;
        public final float planReadChance;
        public final int planReadMinTicks;
        public final int planReadMaxTicks;
        /**
         * Radius in blocks from the anchor that counts as the settlement itself.
         *
         * <p>Buildings marked {@code core} must land inside it and buildings marked {@code
         * outer} must land past it; anything {@code any} — streets, walls, resource sites —
         * ignores it. Set it to 0 to switch zoning off entirely, which is also what makes this
         * safe to ship: one number reverts the whole behaviour without touching code.
         *
         * <p>32 by default, which is about two street pieces out from a 15x14 starter: enough
         * for the square, its houses, the well and the green, and not so much that the fields
         * end up in the middle of the village. It is deliberately a flat radius for now —
         * eventually the wall ring should BE the boundary, since {@code military} is already a
         * category and a wall already knows where inside is.
         */
        public final int coreRadius;
        public final List<ActivityDef> secondaryActivities;

        public Config(double walkSpeed, double blockReachDistance, int blockDelayTicks,
                      int burstPauseMinTicks, int burstPauseMaxTicks, int maxBurstExtraBlocks,
                      int stuckFallbackTicks, int movingTimeoutTicks,
                      float planReadChance, int planReadMinTicks, int planReadMaxTicks,
                      int coreRadius, List<ActivityDef> secondaryActivities) {
            this.walkSpeed = walkSpeed;
            this.blockReachDistance = blockReachDistance;
            this.blockDelayTicks = blockDelayTicks;
            this.burstPauseMinTicks = burstPauseMinTicks;
            this.burstPauseMaxTicks = burstPauseMaxTicks;
            this.maxBurstExtraBlocks = maxBurstExtraBlocks;
            this.stuckFallbackTicks = stuckFallbackTicks;
            this.movingTimeoutTicks = movingTimeoutTicks;
            this.planReadChance = planReadChance;
            this.planReadMinTicks = planReadMinTicks;
            this.planReadMaxTicks = planReadMaxTicks;
            this.coreRadius = coreRadius;
            this.secondaryActivities = secondaryActivities;
        }
    }

    private static final Config DEFAULTS = new Config(
        0.6, 6.0, 4, 10, 18, 2, 100, 3600, 0.05f, 15, 35, 32, List.of()
    );

    private static Config loaded = DEFAULTS;

    public static Config get() { return loaded; }

    public static void reload(MinecraftServer server) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "jobs/builder.json");
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            loaded = DEFAULTS;
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            List<ActivityDef> activities = new ArrayList<>();
            if (json.has("secondary_activities")) {
                for (JsonElement el : json.getAsJsonArray("secondary_activities")) {
                    JsonObject obj = el.getAsJsonObject();
                    activities.add(new ActivityDef(
                        obj.get("requiredBuilding").getAsString(),
                        obj.get("heldItem").getAsString(),
                        AnimationType.valueOf(obj.get("animationType").getAsString()),
                        obj.has("target_block") ? obj.get("target_block").getAsString() : null
                    ));
                }
            }
            loaded = new Config(
                getDbl(json, "walk_speed",                     DEFAULTS.walkSpeed),
                getDbl(json, "block_reach_distance",           DEFAULTS.blockReachDistance),
                getInt(json, "block_delay_ticks",              DEFAULTS.blockDelayTicks),
                getInt(json, "burst_pause_min_ticks",          DEFAULTS.burstPauseMinTicks),
                getInt(json, "burst_pause_max_ticks",          DEFAULTS.burstPauseMaxTicks),
                getInt(json, "max_burst_extra_blocks",         DEFAULTS.maxBurstExtraBlocks),
                getInt(json, "stuck_fallback_ticks",           DEFAULTS.stuckFallbackTicks),
                getInt(json, "moving_timeout_ticks",           DEFAULTS.movingTimeoutTicks),
                getFlt(json, "plan_read_chance",               DEFAULTS.planReadChance),
                getInt(json, "plan_read_min_ticks",            DEFAULTS.planReadMinTicks),
                getInt(json, "plan_read_max_ticks",            DEFAULTS.planReadMaxTicks),
                getInt(json, "core_radius",                    DEFAULTS.coreRadius),
                activities
            );
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
