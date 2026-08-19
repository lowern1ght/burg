package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link StructuralFlags}: the factory
 * collapses the empty case to {@link StructuralFlags#NONE}, the three
 * boolean accessors agree with the {@link StructuralFlags#isEmpty()} /
 * {@link StructuralFlags#isAnySet()} / {@link StructuralFlags#isComplete()}
 * shape, and the act-4 predicate's permissive "any-progress-qualifies"
 * rule survives every per-flag mutation. Each assertion is written to kill
 * a specific mutant (a flipped OR, a swapped sentinel, a relaxed factory)
 * — not to mirror the Javadoc.
 */
class StructuralFlagsMutationTest {

    @Test
    @DisplayName("of(...) collapses the all-false input to NONE — referential stability")
    void ofAllFalseCollapsesToNone() {
        StructuralFlags viaFactory = StructuralFlags.of(false, false, false);
        assertSame(StructuralFlags.NONE, viaFactory,
            "the factory must return NONE for the all-false case (kills a fresh-record mutant)");
    }

    @Test
    @DisplayName("of(...) with any single true returns a fresh, non-NONE record")
    void ofAnyTrueReturnsFreshRecord() {
        assertAll(
            () -> assertNotSame(StructuralFlags.NONE, StructuralFlags.of(true, false, false),
                "core_populated alone must not collapse to NONE"),
            () -> assertNotSame(StructuralFlags.NONE, StructuralFlags.of(false, true, false),
                "industry_zoned alone must not collapse to NONE"),
            () -> assertNotSame(StructuralFlags.NONE, StructuralFlags.of(false, false, true),
                "road_laid alone must not collapse to NONE")
        );
    }

    @Test
    @DisplayName("isEmpty / isAnySet / isComplete agree for every flag combination")
    void predicateShapeAgreesAcrossCombinations() {
        boolean[] values = { false, true };
        for (boolean core : values) {
            for (boolean industry : values) {
                for (boolean road : values) {
                    StructuralFlags flags = StructuralFlags.of(core, industry, road);
                    boolean any = core || industry || road;
                    boolean all = core && industry && road;
                    assertEquals(!any, flags.isEmpty(),
                        "isEmpty must equal !isAnySet for (" + core + "," + industry + "," + road + ")");
                    assertEquals(any, flags.isAnySet(),
                        "isAnySet must equal (core || industry || road) for ("
                            + core + "," + industry + "," + road + ")");
                    assertEquals(all, flags.isComplete(),
                        "isComplete must equal (core && industry && road) for ("
                            + core + "," + industry + "," + road + ")");
                    // Sanity: at most one of {empty, complete} is true.
                    assertFalse(flags.isEmpty() && flags.isComplete(),
                        "isEmpty and isComplete cannot both be true for ("
                            + core + "," + industry + "," + road + ")");
                }
            }
        }
    }

    @Test
    @DisplayName("isEmpty / isAnySet / isComplete agree for the NONE sentinel too")
    void predicateShapeOnNone() {
        assertTrue(StructuralFlags.NONE.isEmpty(),
            "NONE.isEmpty() must read true (kills a flipped-comparator mutant)");
        assertFalse(StructuralFlags.NONE.isAnySet(),
            "NONE.isAnySet() must read false (kills a relaxed-OR mutant)");
        assertFalse(StructuralFlags.NONE.isComplete(),
            "NONE.isComplete() must read false (kills a relaxed-AND mutant)");
    }

    @Test
    @DisplayName("single-flag permutations produce the partial predicate shape — none false")
    void singleFlagPermutationsArePartial() {
        boolean[][] singleFlagCases = {
            { true, false, false },
            { false, true, false },
            { false, false, true }
        };
        for (boolean[] single : singleFlagCases) {
            StructuralFlags flags = StructuralFlags.of(single[0], single[1], single[2]);
            assertTrue(flags.isAnySet(),
                "single-flag case (" + single[0] + "," + single[1] + "," + single[2]
                    + ") must read as isAnySet (kills a strict-AND mutant on isAnySet)");
            assertFalse(flags.isEmpty(),
                "single-flag case (" + single[0] + "," + single[1] + "," + single[2]
                    + ") must NOT read as isEmpty");
            assertFalse(flags.isComplete(),
                "single-flag case (" + single[0] + "," + single[1] + "," + single[2]
                    + ") must NOT read as isComplete");
        }
    }

    @Test
    @DisplayName("record accessors echo the constructor input — no derived-state mutations")
    void accessorsEchoConstructorInput() {
        boolean[][] cases = {
            { true, true, true },
            { true, true, false },
            { true, false, true },
            { false, true, true },
            { true, false, false },
            { false, true, false },
            { false, false, true },
            { false, false, false }
        };
        for (boolean[] c : cases) {
            StructuralFlags flags = new StructuralFlags(c[0], c[1], c[2]);
            assertEquals(c[0], flags.corePopulated(),
                "core_populated echoes the ctor input (" + c[0] + "," + c[1] + "," + c[2] + ")");
            assertEquals(c[1], flags.industryZoned(),
                "industry_zoned echoes the ctor input (" + c[0] + "," + c[1] + "," + c[2] + ")");
            assertEquals(c[2], flags.roadLaid(),
                "road_laid echoes the ctor input (" + c[0] + "," + c[1] + "," + c[2] + ")");
        }
    }
}
