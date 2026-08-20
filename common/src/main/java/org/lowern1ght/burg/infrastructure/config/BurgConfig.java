package org.lowern1ght.burg.infrastructure.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.lowern1ght.burg.people.BuildCadenceMultiplier;
import org.lowern1ght.burg.people.GrowthMultiplier;

/**
 * Configuration data for the mod, expressed as a NeoForge {@link ModConfigSpec}
 * and read into the bare-JVM population simulation via {@link GrowthMultiplier}.
 *
 * <p>This class holds the <em>data</em> side of the config: the typed field
 * ({@link #VILLAGER_GROWTH_MULTIPLIER}) and the spec ({@link #SPEC}). The
 * <em>screen</em> side lives in the {@code neoforge} module (Cloth is a
 * client-only API; the screen builder is in {@code OuatForgeClient}).
 *
 * <p><b>Why this lives in {@code common}, not {@code neoforge}.</b> The
 * data must be readable by the bare-JVM simulation under
 * {@code :common:test} — the same simulation the scale test exercises —
 * so the spec has to compile against NeoForge's common package
 * ({@code net.neoforged.neoforge.common.ModConfigSpec}), which is on the
 * {@code :common} classpath via the ModDev plugin. The screen, in
 * contrast, imports Minecraft client classes and stays in the
 * loader-specific module.
 *
 * <p><b>How the wire site reads it.</b> {@link GrowthMultiplier#current()}
 * is the single source of truth for the simulation: the {@code DaySim}
 * birth loop calls {@code GrowthMultiplier.current().apply(candidates)},
 * with no knowledge of this class. The spec value is pushed into the
 * current multiplier at startup and on every config reload by
 * {@link #refreshMultiplier()}, called from the
 * {@code FMLCommonSetupEvent} and the {@code ModConfigEvent.Reloading}
 * listeners wired up in {@code OuatForge}.
 *
 * <p>ADR-0021.
 */
public final class BurgConfig {

    /**
     * The single config knob the foundation carves — a multiplier on the
     * per-day birth-chance candidate count. Default 1.0 = vanilla behaviour;
     * 0.5 = half-rate; 2.0 = double-rate. The clamp to {@code [0.5, 2.0]} is
     * the domain invariant; the spec's {@code defineInRange} mirrors it so
     * a bad value in the TOML file is caught at load time as well.
     */
    public static final ModConfigSpec.DoubleValue VILLAGER_GROWTH_MULTIPLIER;

    /**
     * Global multiplier on the per-building production cadence. Default 1.0
     * = vanilla cadence; 2.0 = each building fires its production rule twice
     * as often; 0.5 = half as often. Clamped to {@code [0.25, 4.0]} on the
     * same rationale as {@link #VILLAGER_GROWTH_MULTIPLIER} — the domain
     * invariant lives on the {@link BuildCadenceMultiplier} value object and
     * the spec's {@code defineInRange} mirrors it so a bad TOML value is
     * caught at load.
     */
    public static final ModConfigSpec.DoubleValue BUILD_CADENCE_MULTIPLIER;

    /**
     * Cooldown between raids, in seconds. Default 600 (= 10 minutes of
     * play time). The clamp {@code [60, 86400]} keeps a misconfigured
     * value from producing a "raids every tick" or "no raids for a year"
     * world without a save-then-restart round-trip.
     */
    public static final ModConfigSpec.IntValue RAID_COOLDOWN_SECONDS;

    /**
     * Standing threshold a town must cross before the act transition
     * (CONSTRUCTION → SUPPLY) fires. Default 50 — the midpoint of the
     * standing scale. The spec stores it as a double so the Cloth slider
     * can be sub-integer if a datapack ever wants to.
     */
    public static final ModConfigSpec.DoubleValue ACT_THRESHOLD;

    /** The spec, registered with NeoForge at mod construction. */
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
            "Burg configuration. All values are loaded from config/burg-common.toml "
                + "and can be edited from the Mods → Burg → Config screen."
        );
        VILLAGER_GROWTH_MULTIPLIER = builder
            .comment(
                "Scales how quickly new villagers join established towns. 1.0 = default. "
                    + "Higher = faster growth. Range: " + GrowthMultiplier.MIN + " .. " + GrowthMultiplier.MAX + "."
            )
            .defineInRange(
                "villagerGrowthMultiplier",
                GrowthMultiplier.DEFAULT_VALUE,
                GrowthMultiplier.MIN,
                GrowthMultiplier.MAX
            );
        BUILD_CADENCE_MULTIPLIER = builder
            .comment(
                "Scales how often placed buildings fire their production rule. 1.0 = default. "
                    + "Higher = faster production. Range: " + BuildCadenceMultiplier.MIN + " .. " + BuildCadenceMultiplier.MAX + "."
            )
            .defineInRange(
                "buildCadenceMultiplier",
                BuildCadenceMultiplier.DEFAULT_VALUE,
                BuildCadenceMultiplier.MIN,
                BuildCadenceMultiplier.MAX
            );
        RAID_COOLDOWN_SECONDS = builder
            .comment(
                "Cooldown between raids in seconds. Default 600 (10 minutes). Range: 60 .. 86400."
            )
            .defineInRange(
                "raidCooldownSeconds",
                600,
                60,
                86_400
            );
        ACT_THRESHOLD = builder
            .comment(
                "Standing threshold the CONSTRUCTION → SUPPLY transition fires at. Default 50. "
                    + "Range: 0 .. 100."
            )
            .defineInRange(
                "actThreshold",
                50.0,
                0.0,
                100.0
            );
        SPEC = builder.build();
    }

    /**
     * Push the spec's current value into the bare-JVM simulation.
     *
     * <p>Called once on {@code FMLCommonSetupEvent} and again on every
     * {@code ModConfigEvent.Reloading} so user edits in the GUI take
     * effect on the next simulation tick without a world reload.
     */
    public static void refreshMultiplier() {
        GrowthMultiplier.setCurrent(new GrowthMultiplier(VILLAGER_GROWTH_MULTIPLIER.get()));
    }

    /**
     * Set the spec's value to the given multiplier, then push it into the
     * bare-JVM simulation.
     *
     * <p>Used as the {@code setSaveConsumer} of the Cloth Config entry, so
     * the {@link net.neoforged.neoforge.common.IConfigSpec} holds the
     * canonical value and {@link GrowthMultiplier#current()} mirrors it
     * for the simulation.
     */
    public static void refreshMultiplier(double value) {
        VILLAGER_GROWTH_MULTIPLIER.set(value);
        GrowthMultiplier.setCurrent(new GrowthMultiplier(value));
    }

    /**
     * Push the spec's current value into the bare-JVM simulation.
     *
     * <p>Mirrors {@link #refreshMultiplier()} for the build-cadence knob.
     * Called once on {@code FMLCommonSetupEvent} and again on every
     * {@code ModConfigEvent.Reloading} so user edits in the GUI take
     * effect on the next production tick without a world reload.
     */
    public static void refreshBuildCadence() {
        BuildCadenceMultiplier.setCurrent(new BuildCadenceMultiplier(BUILD_CADENCE_MULTIPLIER.get()));
    }

    /**
     * Set the spec's value to the given multiplier, then push it into the
     * bare-JVM simulation.
     *
     * <p>Used as the {@code setSaveConsumer} of the Cloth Config entry, so
     * the {@link net.neoforged.neoforge.common.IConfigSpec} holds the
     * canonical value and {@link BuildCadenceMultiplier#current()} mirrors
     * it for the production tick.
     */
    public static void refreshBuildCadence(double value) {
        BUILD_CADENCE_MULTIPLIER.set(value);
        BuildCadenceMultiplier.setCurrent(new BuildCadenceMultiplier(value));
    }

    private BurgConfig() {}
}
