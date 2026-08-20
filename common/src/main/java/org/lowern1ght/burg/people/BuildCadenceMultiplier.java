package org.lowern1ght.burg.people;

/**
 * Multiplier on the per-building production cadence, as a value object.
 *
 * <p>Mirrors {@link GrowthMultiplier}: a single {@code current} slot the
 * infrastructure pushes into on mod-bus init and on every config reload,
 * and a default the bare-JVM tests use until something wires an override.
 * The {@code apply(int)} floor is "1 tick" so a building whose base
 * cadence is non-zero still ticks at the slowest user setting — a town
 * without production has bigger problems than a slow production line.
 *
 * <p>Where {@link GrowthMultiplier} scales <em>how many</em> candidates
 * each day produces, this scales <em>how often</em> a placed building
 * fires its production rule. Default 1.0 = vanilla cadence. A value of
 * 2.0 halves {@code everyTicks} (production fires twice as often); a
 * value of 0.5 doubles it (production fires half as often). The
 * {@code 1-tick} floor caps the upper end so a user can't drive a
 * building into a "produce every tick" runaway by dialling the slider.
 *
 * <p>No Minecraft import, by rule. See {@link GrowthMultiplier}.
 */
public final class BuildCadenceMultiplier {

    /** Hard floor. Below this a building that wants to fire every 4 ticks would fire every 8. */
    public static final double MIN = 0.25;

    /** Hard ceiling. Above this and the cadence diverges from the everyTicks baseline. */
    public static final double MAX = 4.0;

    /** The neutral value — vanilla cadence. */
    public static final double DEFAULT_VALUE = 1.0;

    /** A pre-built instance for the default, so callers do not allocate a fresh one. */
    public static final BuildCadenceMultiplier DEFAULT = new BuildCadenceMultiplier(DEFAULT_VALUE);

    /**
     * The currently-active multiplier. Infrastructure sets it once at mod-bus
     * init; tests read/write it freely. Kept volatile because the config
     * screen may rebuild the value on a different thread.
     */
    private static volatile BuildCadenceMultiplier current = DEFAULT;

    private final double value;

    public BuildCadenceMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("BuildCadenceMultiplier must be finite, got " + value);
        }
        this.value = Math.max(MIN, Math.min(MAX, value));
    }

    public double value() { return value; }

    /**
     * Apply the multiplier to a base cadence in ticks. The floor of 1 tick
     * is deliberate — a building that produces every 4 ticks at default
     * still ticks at least every 1 tick at the fastest user setting, so
     * the wire site never goes into a tight CPU loop no matter what the
     * user dials the slider to.
     */
    public int apply(int everyTicks) {
        if (everyTicks <= 0) return 1;
        int scaled = (int) Math.round(everyTicks / value);
        return Math.max(1, scaled);
    }

    /** The multiplier currently in effect at the wire site. */
    public static BuildCadenceMultiplier current() { return current; }

    /**
     * Replace the active multiplier. Called from infrastructure (Cloth Config)
     * at mod-bus init and on config reload; the test suite uses it to
     * exercise a non-default value without touching the GUI.
     */
    public static void setCurrent(BuildCadenceMultiplier multiplier) {
        if (multiplier == null) {
            throw new NullPointerException("multiplier");
        }
        current = multiplier;
    }

    /** Reset to {@link #DEFAULT}. Used by tests so a {@code setCurrent} in one test does not leak. */
    public static void resetCurrent() { current = DEFAULT; }

    @Override
    public String toString() { return "BuildCadenceMultiplier[" + value + "]"; }

    @Override
    public boolean equals(Object o) {
        return o instanceof BuildCadenceMultiplier other && Double.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() { return Double.hashCode(value); }
}