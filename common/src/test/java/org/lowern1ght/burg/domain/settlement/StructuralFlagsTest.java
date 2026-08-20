package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structural act-4 flag-set, in pure JUnit. Bare JVM, no Minecraft —
 * the triple is the additive default for towns whose structural state has
 * not been queried yet, and the {@link StructuralFlags#NONE} sentinel is
 * the referentially-stable identity the {@code Town#hubMode()} predicate
 * compares against.
 *
 * <p>Three correctness traps the unit tests are explicitly here to catch:
 * (1) the {@code of(false, false, false)} factory must collapse to the
 * {@link StructuralFlags#NONE} sentinel — a fresh record for the default
 * would silently break the predicate's equality checks; (2) the
 * {@link #isAnySet()} / {@link #isEmpty()} / {@link #isComplete()} shape
 * must agree (no flag set ⇒ {@code isEmpty}, every flag set ⇒
 * {@code isComplete}, mixed ⇒ {@code isAnySet}); and (3) a single
 * {@code true} anywhere flips the flag-set from NONE to "partial" — the
 * permissive form of the act-4 gate today.
 */
class StructuralFlagsTest {

    @Test
    @DisplayName("NONE is the additive default — every flag unset")
    void noneIsTheDefault() {
        StructuralFlags flags = StructuralFlags.NONE;
        assertAll(
            () -> assertSame(StructuralFlags.NONE, flags,
                "NONE is referentially stable (the additive default sentinel)"),
            () -> assertFalse(flags.corePopulated(),
                "NONE has core_populated = false"),
            () -> assertFalse(flags.industryZoned(),
                "NONE has industry_zoned = false"),
            () -> assertFalse(flags.roadLaid(),
                "NONE has road_laid = false"),
            () -> assertTrue(flags.isEmpty(),
                "NONE is the all-false flag-set (the predicate's 'no progress' floor)"),
            () -> assertFalse(flags.isAnySet(),
                "NONE has no flag set, so isAnySet() must read false"),
            () -> assertFalse(flags.isComplete(),
                "NONE is not the complete flag-set (kills a swapped-comparator mutant)")
        );
    }

    @Test
    @DisplayName("of(false, false, false) returns NONE — referential stability for the empty case")
    void ofAllFalseReturnsNone() {
        assertSame(StructuralFlags.NONE, StructuralFlags.of(false, false, false),
            "the all-false factory call collapses to NONE — never a fresh record");
    }

    @Test
    @DisplayName("any single flag set flips the flag-set out of NONE and into 'partial'")
    void singleFlagFlipsToPartial() {
        assertAll(
            () -> assertTrue(StructuralFlags.of(true, false, false).isAnySet(),
                "core_populated alone is structural partial"),
            () -> assertTrue(StructuralFlags.of(false, true, false).isAnySet(),
                "industry_zoned alone is structural partial"),
            () -> assertTrue(StructuralFlags.of(false, false, true).isAnySet(),
                "road_laid alone is structural partial")
        );
    }

    @Test
    @DisplayName("a partial flag-set is not empty and not complete — mixed shape")
    void partialFlagSetShape() {
        StructuralFlags partial = StructuralFlags.of(true, false, false);
        assertAll(
            () -> assertFalse(partial.isEmpty(),
                "partial is not the all-false sentinel"),
            () -> assertTrue(partial.isAnySet(),
                "partial has at least one flag set"),
            () -> assertFalse(partial.isComplete(),
                "partial is not the all-true flag-set (the future strict predicate)")
        );
    }

    @Test
    @DisplayName("the complete flag-set has every condition met")
    void completeFlagSet() {
        StructuralFlags complete = StructuralFlags.of(true, true, true);
        assertAll(
            () -> assertTrue(complete.corePopulated()),
            () -> assertTrue(complete.industryZoned()),
            () -> assertTrue(complete.roadLaid()),
            () -> assertFalse(complete.isEmpty(),
                "complete is not the all-false sentinel"),
            () -> assertTrue(complete.isAnySet(),
                "complete trivially has flags set"),
            () -> assertTrue(complete.isComplete(),
                "complete has every condition met (the future strict predicate)")
        );
    }

    @Test
    @DisplayName("record equality is value-based — same flags, same equals")
    void recordEqualityIsValueBased() {
        StructuralFlags left = StructuralFlags.of(true, false, true);
        StructuralFlags right = StructuralFlags.of(true, false, true);
        assertAll(
            () -> assertEquals(left, right,
                "two of(...) calls with the same flags are equal"),
            () -> assertEquals(left.hashCode(), right.hashCode(),
                "equal records share a hash code"),
            () -> assertNotEquals(left, StructuralFlags.of(false, true, true),
                "different flag combinations are unequal"),
            () -> assertNotEquals(left, StructuralFlags.NONE,
                "any non-NONE flag-set is unequal to NONE")
        );
    }

    @Test
    @DisplayName("a fresh all-false record equals NONE but is not the same instance")
    void freshAllFalseEqualsNoneButNotSame() {
        StructuralFlags fresh = new StructuralFlags(false, false, false);
        assertAll(
            () -> assertEquals(StructuralFlags.NONE, fresh,
                "record equality collapses the all-false case to NONE"),
            () -> assertNotSame(StructuralFlags.NONE, fresh,
                "the factory's referential-stability guarantee does not extend to the public ctor")
        );
    }

    // ------------------------------------------------------------------------
    // Act-4 follow-up-2 — derivation contract pinned at the value-object
    // level (Town.structuralFlags() itself is MC-typed and can't be
    // bare-JVM-constructed, so the contract it implements is pinned here
    // on the StructuralFlags side). Three claims from the spec:
    //
    //   (1) a Town with no roads/zoning/core-population tracking still
    //       returns a non-zero (permissive) flag-set — the partial
    //       shape of(false, true, true) that today's Town produces
    //       when only industryZoned and roadLaid default to true;
    //   (2) a Town with all three flags explicitly true returns
    //       of(true, true, true) — the complete flag-set;
    //   (3) the NONE flag-set forces hubMode() == CONSTRUCTION
    //       regardless of acquisition — NONE.isAnySet() is the
    //       gate's "no flag set" floor; the Town-level hubMode()
    //       method collapses to CONSTRUCTION when isAnySet is false.
    //
    // All three claims are pinned below; the Town-level wiring is left
    // for the :neoforge test target (or the day Town is refactored to
    // be Minecraft-free).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("partial-permissive shape — of(false, true, true) is non-zero and partial (the act-4 gate's current permissive form)")
    void partialPermissiveShape() {
        // Today's Town.structuralFlags() returns of(corePopulated(),
        // true, true) — core_populated is real (false for an empty or
        // not-yet-built-out town), the other two are permissive. The
        // shape below is the partial-permissive flag-set; the gate's
        // permissive form (isAnySet) must still fire on this shape so
        // acquisition is the binding constraint, not the structural
        // triple.
        StructuralFlags partial = StructuralFlags.of(false, true, true);
        assertAll(
            () -> assertFalse(partial.corePopulated(),
                "the core_populated leg is the real derivation — false here"),
            () -> assertTrue(partial.industryZoned(),
                "industry_zoned is permissive (true) — no zoning field on Town yet"),
            () -> assertTrue(partial.roadLaid(),
                "road_laid is permissive (true) — no per-town road graph yet"),
            () -> assertTrue(partial.isAnySet(),
                "the partial-permissive flag-set has at least one flag set — the gate fires"),
            () -> assertFalse(partial.isEmpty(),
                "the partial-permissive flag-set is not the NONE sentinel"),
            () -> assertFalse(partial.isComplete(),
                "the partial-permissive flag-set is not the complete flag-set")
        );
    }

    @Test
    @DisplayName("NONE is the act-4 gate's no-progress floor — no flag set, hubMode collapses to CONSTRUCTION regardless of acquisition")
    void noneIsGateNoProgressFloor() {
        // The third claim: NONE flag-set means hubMode() returns
        // CONSTRUCTION regardless of acquisition. The Town-level
        // hubMode() check is `a != FREE && structuralFlags().isAnySet()`
        // — when isAnySet is false, the gate collapses to CONSTRUCTION.
        // The shape is pinned here on the value-object side; the
        // Town-level wiring follows from the existing hubMode
        // implementation.
        assertAll(
            () -> assertFalse(StructuralFlags.NONE.isAnySet(),
                "NONE has no flag set — the gate's no-progress floor"),
            () -> assertTrue(StructuralFlags.NONE.isEmpty(),
                "NONE is the all-false sentinel — isEmpty == true"),
            () -> assertFalse(StructuralFlags.NONE.isComplete(),
                "NONE is not the complete flag-set (no condition met)")
        );
    }
}
