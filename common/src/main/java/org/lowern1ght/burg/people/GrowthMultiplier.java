package org.lowern1ght.burg.people;

import java.util.Objects;

/**
 * Multiplier on the birth-rate per couple, as a value object.
 *
 * <p>Clamps to the {@code [MIN, MAX]} band on construction, so a bad value read
 * from the config file cannot escape this class and feed a weird number into
 * {@link DaySim}. The clamp is the domain invariant; the config file is the
 * one place that can produce out-of-band inputs, and the clamp catches them
 * at the edge.
 *
 * <p>No Minecraft import, by rule. See {@link Population}.
 */
public final class GrowthMultiplier {

    /** Hard floor. Below this and the simulation stalls visibly. */
    public static final double MIN = 0.5;

    /** Hard ceiling. Above this and the spawn loop over-shoots in a single day. */
    public static final double MAX = 2.0;

    /** The neutral value — vanilla behaviour. */
    public static final double DEFAULT_VALUE = 1.0;

    /** A pre-built instance for the default, so callers do not allocate a fresh one. */
    public static final GrowthMultiplier DEFAULT = new GrowthMultiplier(DEFAULT_VALUE);

    /**
     * The currently-active multiplier. Infrastructure sets it once at mod-bus
     * init; tests read/write it freely. Kept volatile because the config
     * screen may rebuild the value on a different thread.
     */
    private static volatile GrowthMultiplier current = DEFAULT;

    private final double value;

    public GrowthMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("GrowthMultiplier must be finite, got " + value);
        }
        this.value = Math.max(MIN, Math.min(MAX, value));
    }

    public double value() { return value; }

    /**
     * Apply the multiplier to a count of birth candidates.
     *
     * <p>The floor of {@code 1} is deliberate: a town should always be able
     * to make at least the slow default of progress, even at the lowest
     * user-tunable setting, so the simulation never deadlocks around
     * "rounded down to zero births".
     */
    public int apply(int candidates) {
        if (candidates <= 0) return 0;
        return Math.max(1, (int) Math.round(candidates * value));
    }

    /** The multiplier currently in effect at the wire site. */
    public static GrowthMultiplier current() { return current; }

    /**
     * Replace the active multiplier. Called from infrastructure (Cloth Config)
     * at mod-bus init and on config reload; the test suite uses it to
     * exercise a non-default value without touching the GUI.
     */
    public static void setCurrent(GrowthMultiplier multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        current = multiplier;
    }

    /** Reset to {@link #DEFAULT}. Used by tests so a {@code setCurrent} in one test does not leak. */
    public static void resetCurrent() { current = DEFAULT; }

    @Override
    public String toString() { return "GrowthMultiplier[" + value + "]"; }

    @Override
    public boolean equals(Object o) {
        return o instanceof GrowthMultiplier other && Double.compare(value, other.value) == 0;
    }

    @Override
    public int hashCode() { return Double.hashCode(value); }
}
