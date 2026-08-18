package org.lowern1ght.burg.behavior.morale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The morale bucket boundaries, in pure JUnit.
 *
 * <p>{@link MoraleLevel} deliberately has no Minecraft import, so this test
 * lives under {@code common/src/test/} alongside {@code DayPhaseTest} rather
 * than in the GameTest suite. Boundaries are off-by-one prone — "everything
 * stopped being NEUTRAL at value 60" is exactly the kind of fault that is
 * invisible in the engine tick and instant here.
 *
 * <p>The cutoffs are inclusive at the lower bound and exclusive at the upper
 * (so {@code 20} is the first UNHAPPY tick, and {@code 80} is the first
 * HAPPY tick). The top end is closed by {@code LOYAL} whose upper bound is
 * one past the scale, so {@code fromValue(100)} resolves to {@code LOYAL}.
 */
class MoraleLevelTest {

    @Test
    @DisplayName("the boundary values land in the right bucket")
    void boundaries() {
        assertAll(
            () -> assertEquals(MoraleLevel.HOSTILE, MoraleLevel.fromValue(0),
                "0 is the bottom of HOSTILE"),
            () -> assertEquals(MoraleLevel.UNHAPPY, MoraleLevel.fromValue(20),
                "20 is the bottom of UNHAPPY"),
            () -> assertEquals(MoraleLevel.NEUTRAL, MoraleLevel.fromValue(40),
                "40 is the bottom of NEUTRAL"),
            () -> assertEquals(MoraleLevel.HAPPY, MoraleLevel.fromValue(60),
                "60 is the bottom of HAPPY"),
            () -> assertEquals(MoraleLevel.LOYAL, MoraleLevel.fromValue(80),
                "80 is the bottom of LOYAL"),
            () -> assertEquals(MoraleLevel.LOYAL, MoraleLevel.fromValue(100),
                "100 is still LOYAL (the top bucket is closed at 101)")
        );
    }

    @Test
    @DisplayName("a value just below the upper bound stays in its bucket")
    void justBelowUpper() {
        assertAll(
            () -> assertEquals(MoraleLevel.HOSTILE, MoraleLevel.fromValue(19),
                "19 is still HOSTILE (upper bound 20 is exclusive)"),
            () -> assertEquals(MoraleLevel.UNHAPPY, MoraleLevel.fromValue(39),
                "39 is still UNHAPPY"),
            () -> assertEquals(MoraleLevel.NEUTRAL, MoraleLevel.fromValue(59),
                "59 is still NEUTRAL"),
            () -> assertEquals(MoraleLevel.HAPPY, MoraleLevel.fromValue(79),
                "79 is still HAPPY"),
            () -> assertEquals(MoraleLevel.LOYAL, MoraleLevel.fromValue(99),
                "99 is still LOYAL")
        );
    }

    @Test
    @DisplayName("values past the scale clamp before lookup, not crash")
    void outOfRangeClamps() {
        assertAll(
            () -> assertEquals(MoraleLevel.HOSTILE, MoraleLevel.fromValue(-50),
                "a negative value clamps to 0 (HOSTILE)"),
            () -> assertEquals(MoraleLevel.HOSTILE, MoraleLevel.fromValue(-1),
                "minus-one clamps to 0"),
            () -> assertEquals(MoraleLevel.LOYAL, MoraleLevel.fromValue(101),
                "101 clamps to 100 (LOYAL)"),
            () -> assertEquals(MoraleLevel.LOYAL, MoraleLevel.fromValue(50_000),
                "very large value still clamps to LOYAL")
        );
    }

    @Test
    @DisplayName("every value in 0..100 lands in exactly one bucket")
    void totalAndDisjoint() {
        int[] counts = new int[MoraleLevel.values().length];
        for (int v = 0; v <= 100; v++) {
            counts[MoraleLevel.fromValue(v).ordinal()]++;
        }
        // 101 values (0..100 inclusive) split across five buckets. The
        // boundary sizes are 20, 20, 20, 20, 21 (LOYAL closes the top end).
        assertEquals(20, counts[MoraleLevel.HOSTILE.ordinal()]);
        assertEquals(20, counts[MoraleLevel.UNHAPPY.ordinal()]);
        assertEquals(20, counts[MoraleLevel.NEUTRAL.ordinal()]);
        assertEquals(20, counts[MoraleLevel.HAPPY.ordinal()]);
        assertEquals(21, counts[MoraleLevel.LOYAL.ordinal()]);
    }
}
