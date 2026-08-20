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
 * Value-semantics test for {@link BuildCadenceMultiplier}.
 *
 * <p>The range clamp, the {@code apply(int)} floor, and the static
 * {@code current()} slot are the only things a foundation carve can break
 * without touching {@link ProductionManager} or the per-building cadence
 * wiring. Mirrors {@link GrowthMultiplierTest} in shape — the two
 * multipliers ship together and a reviewer wants them to look the same.
 */
class BuildCadenceMultiplierTest {

    @AfterEach
    void resetToDefault() { BuildCadenceMultiplier.resetCurrent(); }

    @Test
    @DisplayName("default value is 1.0 and the apply floor is 1 tick")
    void defaultValueAndApplyFloor() {
        BuildCadenceMultiplier m = BuildCadenceMultiplier.DEFAULT;
        assertEquals(1.0, m.value(), "DEFAULT is the neutral multiplier");

        // The floor of 1 tick is the contract: even at the fastest user
        // setting, a building's cadence never collapses to zero — the
        // wire site would otherwise loop a tick on a producing building.
        assertAll(
            () -> assertEquals(1, m.apply(0), "non-positive cadence short-circuits to 1"),
            () -> assertEquals(1, m.apply(1), "1-tick cadence stays 1 tick"),
            () -> assertEquals(1, m.apply(-3), "negative cadence is out of contract; floor to 1")
        );
    }

    @Test
    @DisplayName("in-range values round-trip exactly")
    void inRangeRoundTrip() {
        for (double v : new double[] { 0.25, 0.5, 0.75, 1.0, 1.5, 2.0, 4.0 }) {
            BuildCadenceMultiplier m = new BuildCadenceMultiplier(v);
            assertEquals(v, m.value(), "value inside the band is unchanged");
        }
    }

    @Test
    @DisplayName("out-of-range values are clamped, not rejected")
    void outOfRangeClamped() {
        assertAll(
            () -> assertEquals(BuildCadenceMultiplier.MIN, new BuildCadenceMultiplier(-7.0).value(),
                "well below MIN snaps to MIN"),
            () -> assertEquals(BuildCadenceMultiplier.MIN, new BuildCadenceMultiplier(0.24999).value(),
                "epsilon below MIN snaps to MIN"),
            () -> assertEquals(BuildCadenceMultiplier.MAX, new BuildCadenceMultiplier(4.00001).value(),
                "epsilon above MAX snaps to MAX"),
            () -> assertEquals(BuildCadenceMultiplier.MAX, new BuildCadenceMultiplier(9999.0).value(),
                "well above MAX snaps to MAX")
        );
    }

    @Test
    @DisplayName("NaN and infinity are rejected, not silently clamped")
    void nonFiniteRejected() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> new BuildCadenceMultiplier(Double.NaN),
                "NaN is a programmer error, not a config-file artifact"),
            () -> assertThrows(IllegalArgumentException.class, () -> new BuildCadenceMultiplier(Double.POSITIVE_INFINITY),
                "infinity is a programmer error, not a config-file artifact"),
            () -> assertThrows(IllegalArgumentException.class, () -> new BuildCadenceMultiplier(Double.NEGATIVE_INFINITY),
                "negative infinity is a programmer error, not a config-file artifact")
        );
    }

    @Test
    @DisplayName("apply divides the cadence by the multiplier and floors at 1 tick")
    void applyScalingAndFloor() {
        BuildCadenceMultiplier double_ = new BuildCadenceMultiplier(2.0);
        BuildCadenceMultiplier half = new BuildCadenceMultiplier(0.5);
        BuildCadenceMultiplier quad = new BuildCadenceMultiplier(4.0);

        assertAll(
            () -> assertEquals(4, double_.apply(8), "2.0 → halves everyTicks"),
            () -> assertEquals(2, double_.apply(4), "2.0 × 4 = 2"),
            () -> assertEquals(8, half.apply(4), "0.5 → doubles everyTicks"),
            () -> assertEquals(1, double_.apply(2), "2.0 × 2 = 1, no floor needed"),
            () -> assertEquals(1, double_.apply(1), "already 1 tick stays 1 tick"),
            () -> assertEquals(1, quad.apply(4), "4.0 × 4 = 1, the maximum-allowed cadence compression"),
            () -> assertEquals(1, quad.apply(1), "4.0 × 1 = 1, the absolute floor")
        );
    }

    @Test
    @DisplayName("static current() is the DEFAULT until set, then the override")
    void currentSlotLifecycle() {
        assertSame(BuildCadenceMultiplier.DEFAULT, BuildCadenceMultiplier.current(),
            "the slot starts as DEFAULT, before any mod-bus or test wires it");

        BuildCadenceMultiplier override = new BuildCadenceMultiplier(2.0);
        BuildCadenceMultiplier.setCurrent(override);
        assertSame(override, BuildCadenceMultiplier.current(),
            "after setCurrent, the slot returns the override");

        BuildCadenceMultiplier.resetCurrent();
        assertSame(BuildCadenceMultiplier.DEFAULT, BuildCadenceMultiplier.current(),
            "resetCurrent snaps the slot back to DEFAULT");
    }

    @Test
    @DisplayName("null to setCurrent is rejected")
    void nullSetCurrentRejected() {
        assertThrows(NullPointerException.class, () -> BuildCadenceMultiplier.setCurrent(null));
    }

    @Test
    @DisplayName("equals and hashCode follow the value")
    void equalsAndHashCode() {
        BuildCadenceMultiplier a = new BuildCadenceMultiplier(1.5);
        BuildCadenceMultiplier b = new BuildCadenceMultiplier(1.5);
        BuildCadenceMultiplier c = new BuildCadenceMultiplier(2.5);

        assertAll(
            () -> assertEquals(a, b, "same value → equal"),
            () -> assertEquals(a.hashCode(), b.hashCode(), "same value → same hash"),
            () -> assertNotEquals(a, c, "different value → not equal")
        );
    }
}