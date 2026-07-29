package org.dawnoftime.onceuponatown.people;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The day, without a world to have one in.
 *
 * <p>Small, and worth having anyway: the boundaries are off-by-one prone, the function is handed a
 * clock that runs for thousands of days, and "everyone stopped going to bed on day 12" is exactly
 * the sort of fault that is invisible for hours in game and instant here.
 */
class DayPhaseTest {

    @Test
    @DisplayName("the boundaries are vanilla's own, and inclusive at the right end")
    void boundaries() {
        assertAll(
            () -> assertEquals(DayPhase.DAY, DayPhase.of(0), "sunrise is day"),
            () -> assertEquals(DayPhase.DAY, DayPhase.of(11499)),
            () -> assertEquals(DayPhase.DUSK, DayPhase.of(11500), "the sun starts to set"),
            () -> assertEquals(DayPhase.DUSK, DayPhase.of(12999)),
            () -> assertEquals(DayPhase.NIGHT, DayPhase.of(13000), "dark enough for mobs"),
            () -> assertEquals(DayPhase.NIGHT, DayPhase.of(22999)),
            () -> assertEquals(DayPhase.DAWN, DayPhase.of(23000), "first light"),
            () -> assertEquals(DayPhase.DAWN, DayPhase.of(23999))
        );
    }

    @Test
    @DisplayName("it still works on day one thousand")
    void wrapsForever() {
        long tenDays = 10 * DayPhase.DAY_LENGTH;
        assertAll(
            () -> assertEquals(DayPhase.DAY, DayPhase.of(tenDays)),
            () -> assertEquals(DayPhase.NIGHT, DayPhase.of(tenDays + 14000)),
            () -> assertEquals(DayPhase.NIGHT, DayPhase.of(1000 * DayPhase.DAY_LENGTH + 14000),
                "a thousand days in, night is still night")
        );
    }

    @Test
    @DisplayName("a negative clock does not throw or invert")
    void negativeTime() {
        // /time set can go backwards, and a modulus that returns a negative would send everybody
        // to a phase that does not exist. floorMod, not %.
        assertAll(
            () -> assertEquals(DayPhase.DAWN, DayPhase.of(-1), "one tick before sunrise is dawn"),
            () -> assertEquals(DayPhase.NIGHT, DayPhase.of(-10000))
        );
    }

    @Test
    @DisplayName("every tick of the day belongs to exactly one phase")
    void totalAndDisjoint() {
        int day = 0, dusk = 0, night = 0, dawn = 0;
        for (long t = 0; t < DayPhase.DAY_LENGTH; t++) {
            switch (DayPhase.of(t)) {
                case DAY -> day++;
                case DUSK -> dusk++;
                case NIGHT -> night++;
                case DAWN -> dawn++;
            }
        }
        final int d = day, k = dusk, n = night, w = dawn;
        assertAll(
            () -> assertEquals(DayPhase.DAY_LENGTH, d + k + n + w,
                "the four phases must tile the day exactly, with no tick in two or in none"),
            () -> assertEquals(11500, d),
            () -> assertEquals(1500, k, "dusk is a window, not an instant -- long enough to "
                + "walk home in, which is the whole reason it is its own phase"),
            () -> assertEquals(10000, n),
            () -> assertEquals(1000, w)
        );
    }

    @Test
    @DisplayName("the three questions the routine actually asks")
    void predicates() {
        assertAll(
            () -> assertTrue(DayPhase.DAY.isWorkingTime()),
            () -> assertFalse(DayPhase.DUSK.isWorkingTime(), "work stops when the light goes"),
            () -> assertTrue(DayPhase.DUSK.isRestingTime(), "dusk is for walking home"),
            () -> assertFalse(DayPhase.DUSK.isSleepingTime(), "but not yet for lying down"),
            () -> assertTrue(DayPhase.NIGHT.isSleepingTime()),
            () -> assertFalse(DayPhase.DAWN.isRestingTime(), "dawn gets you out of bed"),
            () -> assertFalse(DayPhase.DAWN.isWorkingTime(), "and not straight to the bench")
        );
    }
}
