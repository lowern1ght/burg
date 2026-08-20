package org.lowern1ght.burg.people;

import java.util.Objects;

/**
 * Cooldown between raids, in seconds, as a value object.
 *
 * <p>Mirrors {@link GrowthMultiplier}: a single {@code current} slot the
 * infrastructure pushes into on mod-bus init and on every config reload,
 * and a default the bare-JVM tests use until something wires an override.
 * The clamp to {@code [60, 86400]} is the domain invariant; the spec's
 * {@code defineInRange} mirrors it so a bad TOML value is caught at load
 * time as well.
 *
 * <p>Wire-format semantics — the next raid's earliest-fire time is
 * {@code previousFire + current().cooldownTicks()}. {@link #earliestNextFire(long)}
 * centralises that arithmetic so the {@code RaidManager} call site does
 * not redo the seconds-to-ticks conversion on every tick. The conversion
 * itself is the Minecraft standard 20 ticks per second of play time.
 *
 * <p>No Minecraft import, by rule. See {@link GrowthMultiplier}.
 */
public final class RaidConfig {

    /** Hard floor. Below this and a misconfigured world fires raids in a tight loop. */
    public static final int MIN_SECONDS = 60;

    /** Hard ceiling. Above this and a misconfigured world never sees a raid in a session. */
    public static final int MAX_SECONDS = 86_400;

    /** The neutral value — 10 minutes between raids. */
    public static final int DEFAULT_SECONDS = 600;

    /** A pre-built instance for the default, so callers do not allocate a fresh one. */
    public static final RaidConfig DEFAULT = new RaidConfig(DEFAULT_SECONDS);

    /** Minecraft runs at 20 ticks per second of play time. The conversion lives here, once. */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * The currently-active cooldown. Infrastructure sets it once at mod-bus
     * init; tests read/write it freely. Kept volatile because the config
     * screen may rebuild the value on a different thread.
     */
    private static volatile RaidConfig current = DEFAULT;

    private final int seconds;

    public RaidConfig(int seconds) {
        this.seconds = Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }

    /** The configured cooldown, in seconds. The value is range-clamped on construction. */
    public int seconds() { return seconds; }

    /**
     * The configured cooldown, in ticks. This is the form the wire site
     * uses — the {@code RaidManager} compares {@code gameTime} (a long
     * tick count) against {@code previousFire + cooldownTicks()}.
     */
    public int cooldownTicks() {
        return seconds * TICKS_PER_SECOND;
    }

    /**
     * The earliest tick the next raid may fire, given the tick of the
     * previous raid. Equivalent to {@code previousFire + cooldownTicks()};
     * the wire-format semantics the {@code hub-becomes-window} spec names.
     */
    public long earliestNextFire(long previousFire) {
        return previousFire + cooldownTicks();
    }

    /** The cooldown currently in effect at the wire site. */
    public static RaidConfig current() { return current; }

    /**
     * Replace the active cooldown. Called from infrastructure (Cloth Config)
     * at mod-bus init and on config reload; the test suite uses it to
     * exercise a non-default value without touching the GUI.
     */
    public static void setCurrent(RaidConfig config) {
        Objects.requireNonNull(config, "config");
        current = config;
    }

    /** Reset to {@link #DEFAULT}. Used by tests so a {@code setCurrent} in one test does not leak. */
    public static void resetCurrent() { current = DEFAULT; }

    @Override
    public String toString() { return "RaidConfig[" + seconds + "s/" + cooldownTicks() + "ticks]"; }

    @Override
    public boolean equals(Object o) {
        return o instanceof RaidConfig other && seconds == other.seconds;
    }

    @Override
    public int hashCode() { return Integer.hashCode(seconds); }
}
