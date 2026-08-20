package org.lowern1ght.burg.people;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value-semantics test for {@link RaidConfig}.
 *
 * <p>The range clamp, the seconds-to-ticks conversion
 * ({@link RaidConfig#cooldownTicks()}), the next-fire arithmetic
 * ({@link RaidConfig#earliestNextFire(long)}), and the static
 * {@code current()} slot are the only things a foundation carve can
 * break without touching {@code RaidManager}, so they are the only
 * things pinned here. Mirrors {@link GrowthMultiplierTest} in shape
 * — the two knobs ship together and a reviewer wants them to look
 * the same.
 */
class RaidConfigTest {

    @AfterEach
    void resetToDefault() { RaidConfig.resetCurrent(); }

    @Test
    @DisplayName("default is 600 seconds = 12000 ticks; next-fire arithmetic adds the cooldown to previousFire")
    void defaultValueAndConversion() {
        RaidConfig c = RaidConfig.DEFAULT;
        assertEquals(600, c.seconds(), "DEFAULT is 10 minutes of play time");
        assertEquals(12_000, c.cooldownTicks(), "DEFAULT = 600s * 20 tps = 12000 ticks");

        // The wire-format semantics: earliestNextFire(prev) = prev + cooldownTicks.
        assertAll(
            () -> assertEquals(12_000L, c.earliestNextFire(0L),
                "first raid may fire at cooldownTicks — additive default for a town that never fired"),
            () -> assertEquals(120_000L, c.earliestNextFire(108_000L),
                "10x DEFAULT cooldown after a previous raid at tick 108000 → next fire at 120000"),
            () -> assertEquals(0L, c.earliestNextFire(-12_000L),
                "negative previousFire clamps the next-fire to 0 — the very first tick")
        );
    }

    @Test
    @DisplayName("in-range seconds round-trip exactly through the seconds() accessor")
    void inRangeRoundTrip() {
        for (int s : new int[] { 60, 300, 600, 1800, 3600, 86_400 }) {
            RaidConfig c = new RaidConfig(s);
            assertEquals(s, c.seconds(), "value inside the band is unchanged");
        }
    }

    @Test
    @DisplayName("cooldownTicks multiplies seconds by 20 — the Minecraft ticks-per-second constant")
    void cooldownTicksMultipliesByTps() {
        assertAll(
            () -> assertEquals(1200, new RaidConfig(60).cooldownTicks(),
                "60s → 60 * 20 = 1200 ticks (the floor — 1 minute of play time)"),
            () -> assertEquals(6000, new RaidConfig(300).cooldownTicks(),
                "300s → 5 minutes of play time"),
            () -> assertEquals(36_000, new RaidConfig(1800).cooldownTicks(),
                "1800s → 30 minutes of play time"),
            () -> assertEquals(86_400 * 20, new RaidConfig(86_400).cooldownTicks(),
                "86400s → 1 day of play time, the ceiling")
        );
    }

    @Test
    @DisplayName("out-of-range seconds are clamped, not rejected")
    void outOfRangeClamped() {
        assertAll(
            () -> assertEquals(RaidConfig.MIN_SECONDS, new RaidConfig(0).seconds(),
                "well below MIN snaps to MIN — the lower bound is 60, not zero"),
            () -> assertEquals(RaidConfig.MIN_SECONDS, new RaidConfig(-100).seconds(),
                "negative values snap to MIN"),
            () -> assertEquals(RaidConfig.MIN_SECONDS, new RaidConfig(59).seconds(),
                "epsilon below MIN snaps to MIN"),
            () -> assertEquals(RaidConfig.MAX_SECONDS, new RaidConfig(86_401).seconds(),
                "epsilon above MAX snaps to MAX"),
            () -> assertEquals(RaidConfig.MAX_SECONDS, new RaidConfig(1_000_000).seconds(),
                "well above MAX snaps to MAX")
        );
    }

    @Test
    @DisplayName("static current() is the DEFAULT until set, then the override")
    void currentSlotLifecycle() {
        assertSame(RaidConfig.DEFAULT, RaidConfig.current(),
            "the slot starts as DEFAULT, before any mod-bus or test wires it");

        RaidConfig override = new RaidConfig(1800);
        RaidConfig.setCurrent(override);
        assertSame(override, RaidConfig.current(),
            "after setCurrent, the slot returns the override");

        RaidConfig.resetCurrent();
        assertSame(RaidConfig.DEFAULT, RaidConfig.current(),
            "resetCurrent snaps the slot back to DEFAULT");
    }

    @Test
    @DisplayName("null to setCurrent is rejected")
    void nullSetCurrentRejected() {
        assertThrows(NullPointerException.class, () -> RaidConfig.setCurrent(null));
    }

    @Test
    @DisplayName("equals and hashCode follow the seconds value")
    void equalsAndHashCode() {
        RaidConfig a = new RaidConfig(300);
        RaidConfig b = new RaidConfig(300);
        RaidConfig c = new RaidConfig(600);

        assertAll(
            () -> assertEquals(a, b, "same seconds → equal"),
            () -> assertEquals(a.hashCode(), b.hashCode(), "same seconds → same hash"),
            () -> assertNotEquals(a, c, "different seconds → not equal")
        );
    }

    @Test
    @DisplayName("toString includes both seconds and cooldown ticks for log diagnostics")
    void toStringShape() {
        String s = RaidConfig.DEFAULT.toString();
        assertAll(
            () -> assertTrue(s.contains("600"), "toString names the seconds value"),
            () -> assertTrue(s.contains("12000"), "toString names the cooldown-ticks value"),
            () -> assertFalse(s.isEmpty(), "toString is never empty")
        );
    }
}
