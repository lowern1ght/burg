package org.lowern1ght.burg.people;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Value-semantics test for {@link GrowthMultiplier}.
 *
 * <p>The range clamp, the {@code apply(int)} floor, and the static
 * {@code current()} slot are the only things a foundation carve can break
 * without touching {@link DaySim} or {@link Population}, so they are the
 * only things pinned here. The Cloth screen and the {@code ModConfigSpec}
 * live in the infrastructure layer and are exercised in-game, not on
 * this bare-JVM classpath.
 */
class GrowthMultiplierTest {

    @AfterEach
    void resetToDefault() { GrowthMultiplier.resetCurrent(); }

    @Test
    @DisplayName("default value is 1.0 and the apply floor is 1")
    void defaultValueAndApplyFloor() {
        GrowthMultiplier m = GrowthMultiplier.DEFAULT;
        assertEquals(1.0, m.value(), "DEFAULT is the neutral multiplier");

        // The floor of 1 is the contract: even at the lowest user setting,
        // a town can make at least slow progress.
        assertAll(
            () -> assertEquals(0, m.apply(0), "no candidates → no births (caller's job)"),
            () -> assertEquals(1, m.apply(1), "1 candidate → at least 1, never zero"),
            () -> assertEquals(0, m.apply(-3), "negative candidates are out of contract")
        );
    }

    @Test
    @DisplayName("in-range values round-trip exactly")
    void inRangeRoundTrip() {
        for (double v : new double[] { 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 }) {
            GrowthMultiplier m = new GrowthMultiplier(v);
            assertEquals(v, m.value(), "value inside the band is unchanged");
        }
    }

    @Test
    @DisplayName("out-of-range values are clamped, not rejected")
    void outOfRangeClamped() {
        assertAll(
            () -> assertEquals(GrowthMultiplier.MIN, new GrowthMultiplier(-7.0).value(),
                "well below MIN snaps to MIN"),
            () -> assertEquals(GrowthMultiplier.MIN, new GrowthMultiplier(0.49999).value(),
                "epsilon below MIN snaps to MIN"),
            () -> assertEquals(GrowthMultiplier.MAX, new GrowthMultiplier(2.00001).value(),
                "epsilon above MAX snaps to MAX"),
            () -> assertEquals(GrowthMultiplier.MAX, new GrowthMultiplier(9999.0).value(),
                "well above MAX snaps to MAX")
        );
    }

    @Test
    @DisplayName("NaN and infinity are rejected, not silently clamped")
    void nonFiniteRejected() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> new GrowthMultiplier(Double.NaN),
                "NaN is a programmer error, not a config-file artifact"),
            () -> assertThrows(IllegalArgumentException.class, () -> new GrowthMultiplier(Double.POSITIVE_INFINITY),
                "infinity is a programmer error, not a config-file artifact"),
            () -> assertThrows(IllegalArgumentException.class, () -> new GrowthMultiplier(Double.NEGATIVE_INFINITY),
                "negative infinity is a programmer error, not a config-file artifact")
        );
    }

    @Test
    @DisplayName("apply scales and then floors at 1, never zero")
    void applyScalingAndFloor() {
        GrowthMultiplier half = new GrowthMultiplier(0.5);
        GrowthMultiplier double_ = new GrowthMultiplier(2.0);

        assertAll(
            () -> assertEquals(1, half.apply(1), "0.5 × 1 rounds to 0, but floor is 1"),
            () -> assertEquals(5, half.apply(10), "0.5 × 10 = 5 exactly"),
            () -> assertEquals(3, half.apply(6), "0.5 × 6 = 3 exactly"),
            () -> assertEquals(20, double_.apply(10), "2.0 × 10 = 20 exactly"),
            () -> assertEquals(16, double_.apply(8), "2.0 × 8 = 16 exactly"),
            () -> assertEquals(1, half.apply(2), "0.5 × 2 = 1 exactly (no floor needed)")
        );
    }

    @Test
    @DisplayName("static current() is the DEFAULT until set, then the override")
    void currentSlotLifecycle() {
        assertSame(GrowthMultiplier.DEFAULT, GrowthMultiplier.current(),
            "the slot starts as DEFAULT, before any mod-bus or test wires it");

        GrowthMultiplier override = new GrowthMultiplier(1.5);
        GrowthMultiplier.setCurrent(override);
        assertSame(override, GrowthMultiplier.current(),
            "after setCurrent, the slot returns the override");

        GrowthMultiplier.resetCurrent();
        assertSame(GrowthMultiplier.DEFAULT, GrowthMultiplier.current(),
            "resetCurrent snaps the slot back to DEFAULT");
    }

    @Test
    @DisplayName("null to setCurrent is rejected")
    void nullSetCurrentRejected() {
        assertThrows(NullPointerException.class, () -> GrowthMultiplier.setCurrent(null));
    }

    @Test
    @DisplayName("equals and hashCode follow the value")
    void equalsAndHashCode() {
        GrowthMultiplier a = new GrowthMultiplier(1.25);
        GrowthMultiplier b = new GrowthMultiplier(1.25);
        GrowthMultiplier c = new GrowthMultiplier(1.5);

        assertAll(
            () -> assertEquals(a, b, "same value → equal"),
            () -> assertEquals(a.hashCode(), b.hashCode(), "same value → same hash"),
            () -> assertNotEquals(a, c, "different value → not equal")
        );
    }
}
