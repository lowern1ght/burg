package org.dawnoftime.onceuponatown.behavior.morale;

/**
 * A bucket of the 0..100 morale scale.
 *
 * <p>The cutoffs are inclusive at the lower bound and exclusive at the upper
 * (so {@code 20} is the first {@code UNHAPPY} tick, and {@code 80} is the
 * first {@code HAPPY} tick). The top end is closed by {@code LOYAL} whose
 * upper bound is one past the scale, so {@code fromValue(100)} resolves to
 * {@code LOYAL} without an off-by-one at the edge.
 *
 * <p>This file deliberately has no Minecraft import: the bucket boundaries
 * are a settlement rule, and the rule belongs somewhere it can be reached by
 * a plain JUnit test in {@code common/src/test/}. See
 * {@code MoraleLevelTest} there for the boundary sweep.
 */
public enum MoraleLevel {
    HOSTILE(0, 20),
    UNHAPPY(20, 40),
    NEUTRAL(40, 60),
    HAPPY(60, 80),
    LOYAL(80, 101);

    private final int lowerInclusive;
    private final int upperExclusive;

    MoraleLevel(int lower, int upper) {
        this.lowerInclusive = lower;
        this.upperExclusive = upper;
    }

    /**
     * Map a clamped morale value to its bucket. Negative or oversized values
     * are pulled back into the 0..100 range before lookup so a stray
     * adjustment cannot fall off either end.
     */
    public static MoraleLevel fromValue(int value) {
        if (value < 0) value = 0;
        if (value > 100) value = 100;
        for (MoraleLevel level : values()) {
            if (value >= level.lowerInclusive && value < level.upperExclusive) {
                return level;
            }
        }
        // value == 100 — covered by the LOYAL bucket (80..101), included above.
        // Reachable only if the enum is reshaped so the top bucket is half-open at 100.
        return LOYAL;
    }
}
