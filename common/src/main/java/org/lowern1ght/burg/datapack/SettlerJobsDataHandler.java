package org.lowern1ght.burg.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import org.lowern1ght.burg.Ouat;
import org.lowern1ght.burg.entity.ai.ActivityDef;
import org.lowern1ght.burg.entity.ai.AnimationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a settler does for a living, from {@code data/burg/jobs/settler.json}.
 *
 * <p>Reuses {@link ActivityDef} rather than inventing a job format, because the builder's
 * {@code secondary_activities} already <b>was</b> one: a building to walk to, a tool to hold, an
 * animation, and a block inside that building to stand at. The only thing missing was somebody
 * other than the builder to run it.
 *
 * <p>{@code target_block} is scanned only inside the bounding box of the building it belongs to.
 * That is what makes a common id safe here — {@code oak_log} names the lumberjack's timber stack
 * (15 of them, all lying, measured) without turning every wall in town into a workplace.
 */
public class SettlerJobsDataHandler {

    private static final Gson GSON = new GsonBuilder().create();
    private static final Logger LOGGER = LoggerFactory.getLogger(SettlerJobsDataHandler.class);

    public record Config(
        List<ActivityDef> jobs,
        /** Ticks of standing at the workstation that count as one shift's work. */
        int workTicks,
        /** Skill gained per completed shift. */
        int skillPerShift,
        /** Cap. Five, matching the five rungs a trade has anywhere else in this mod. */
        int maxSkill,
        /** How long one finished shift keeps a workplace counted as manned. */
        int mannedWindowTicks,
        /** Output share of a workplace that HAS a job defined and nobody worked it. */
        double unmannedOutput,
        /** Output added per level of the worker's skill. */
        double skillBonusPerLevel
    ) {}

    private static final Config DEFAULTS = new Config(List.of(), 2400, 1, 5, 6000, 0.5, 0.15);

    private static Config loaded = DEFAULTS;

    public static Config get() { return loaded; }

    public static void reload(MinecraftServer server) {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "jobs/settler.json");
        Optional<Resource> resource = server.getResourceManager().getResource(location);
        if (resource.isEmpty()) {
            LOGGER.warn("[OUAT] No {} -- settlers will have no work to go to", location);
            loaded = DEFAULTS;
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(resource.get().open())) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            List<ActivityDef> jobs = new ArrayList<>();
            if (json.has("jobs")) {
                for (JsonElement el : json.getAsJsonArray("jobs")) {
                    JsonObject o = el.getAsJsonObject();
                    // Both spellings accepted for every key. builder.json mixes camelCase and
                    // snake_case in the same object, and a job silently dropped for being spelled
                    // the other way is a villager standing idle with no error anywhere.
                    String building = str(o, "requiredBuilding", "required_building", null);
                    String held = str(o, "heldItem", "held_item", "minecraft:air");
                    String anim = str(o, "animationType", "animation_type", "CRAFT");
                    String target = str(o, "target_block", "targetBlock", null);
                    if (building == null) {
                        LOGGER.warn("[OUAT] settler job with no requiredBuilding -- skipped");
                        continue;
                    }
                    AnimationType type;
                    try {
                        type = AnimationType.valueOf(anim);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("[OUAT] settler job for '{}' has animationType '{}', which is"
                            + " not one of {} -- using CRAFT", building, anim,
                            java.util.Arrays.toString(AnimationType.values()));
                        type = AnimationType.CRAFT;
                    }
                    jobs.add(new ActivityDef(building, held, type, target));
                }
            }
            loaded = new Config(
                List.copyOf(jobs),
                json.has("work_ticks") ? json.get("work_ticks").getAsInt() : DEFAULTS.workTicks(),
                json.has("skill_per_shift") ? json.get("skill_per_shift").getAsInt() : DEFAULTS.skillPerShift(),
                json.has("max_skill") ? json.get("max_skill").getAsInt() : DEFAULTS.maxSkill(),
                json.has("manned_window_ticks")
                    ? json.get("manned_window_ticks").getAsInt() : DEFAULTS.mannedWindowTicks(),
                json.has("unmanned_output")
                    ? json.get("unmanned_output").getAsDouble() : DEFAULTS.unmannedOutput(),
                json.has("skill_bonus_per_level")
                    ? json.get("skill_bonus_per_level").getAsDouble() : DEFAULTS.skillBonusPerLevel()
            );
            LOGGER.info("[OUAT] Loaded {} settler job(s)", loaded.jobs().size());
        } catch (Exception e) {
            LOGGER.error("[OUAT] Failed to load {}: {} -- settlers will have no work",
                location, e.getMessage());
            loaded = DEFAULTS;
        }
    }

    /** Whether any settler job at all names this building. A building nobody can work at is
     *  never penalised for being unworked. */
    public static boolean hasJob(String defId) {
        for (ActivityDef d : loaded.jobs()) {
            if (d.requiredBuilding().equals(defId)) return true;
        }
        return false;
    }

    /** The jobs available at a building of this def id. */
    public static List<ActivityDef> jobsAt(String defId) {
        List<ActivityDef> out = new ArrayList<>();
        for (ActivityDef d : loaded.jobs()) {
            if (d.requiredBuilding().equals(defId)) out.add(d);
        }
        return out;
    }

    private static String str(JsonObject o, String a, String b, String fallback) {
        if (o.has(a)) return o.get(a).getAsString();
        if (o.has(b)) return o.get(b).getAsString();
        return fallback;
    }
}
