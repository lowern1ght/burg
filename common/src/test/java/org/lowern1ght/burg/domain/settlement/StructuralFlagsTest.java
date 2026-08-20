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
    // Config-and-structural carve — strict derivation contract pinned at the
    // value-object level (Town.structuralFlags() itself is MC-typed and
    // can't be bare-JVM-constructed, so the contract it implements is pinned
    // here on the StructuralFlags side). The act-4 follow-up-2 carve wired
    // structuralFlags() to the per-town stub fields (zoningCount +
    // plannedRoads), and the strict derivation now reads Map.isEmpty() and
    // List.isEmpty() — the partial-permissive shape (of(false, true, true))
    // the previous carve described is no longer what Town produces. The
    // pins below reflect the strict form:
    //
    //   (1) a Town with empty zoningCount + empty plannedRoads returns
    //       of(false, false, false) = NONE — the structural triple
    //       collapses to NONE, gating hubMode() to CONSTRUCTION regardless
    //       of acquisition (the act-5 carve's zoning/road-planner layers
    //       populate the fields and the gate gets its teeth);
    //   (2) a Town with all three flags explicitly true returns
    //       of(true, true, true) — the complete flag-set;
    //   (3) the NONE flag-set forces hubMode() == CONSTRUCTION regardless
    //       of acquisition — NONE.isAnySet() is the gate's "no flag set"
    //       floor; the Town-level hubMode() method collapses to
    //       CONSTRUCTION when isAnySet is false.
    //
    // All three claims are pinned below; the Town-level wiring is left
    // for the :neoforge test target (or the day Town is refactored to
    // be Minecraft-free).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("strict shape — empty fields yield of(false, false, false) = NONE (the act-5 zoning carve's gating floor)")
    void strictEmptyFieldsShape() {
        // Town.structuralFlags() now reads corePopulated(),
        // industryZoned() = !zoningCount.isEmpty(), and roadLaid() =
        // !plannedRoads.isEmpty(). On a fresh save, both stub fields are
        // empty, so the structural triple collapses to NONE. The shape
        // pinned below is what the Town-level derivation produces today
        // and is the strict form the act-4 follow-up was working toward.
        StructuralFlags strict = StructuralFlags.of(false, false, false);
        assertAll(
            () -> assertSame(StructuralFlags.NONE, strict,
                "the all-false shape is the NONE sentinel — referentially stable"),
            () -> assertTrue(strict.isEmpty(),
                "the strict shape is the NONE sentinel — isEmpty == true"),
            () -> assertFalse(strict.isAnySet(),
                "the strict shape has no flag set — the gate's no-progress floor"),
            () -> assertFalse(strict.isComplete(),
                "the strict shape is not the complete flag-set (no condition met)")
        );
    }

    @Test
    @DisplayName("non-empty stub fields → partial flag-set (the act-5 carve's gate-gets-teeth outcome)")
    void nonEmptyStubFieldsFlipGate() {
        // The Town-level derivation produces a non-NONE flag-set the
        // moment the act-5 zoning carve populates zoningCount or the
        // road-planner carve appends to plannedRoads. The pin below
        // documents that single-field flip — either zoning OR road on
        // its own qualifies the town for the act-4 gate's structural
        // leg (acquisition + standing are the binding constraints, the
        // structural triple is the permissive gate). corePopulated is
        // the real derivation, so it stays false on this fixture.
        StructuralFlags zoningOnly = StructuralFlags.of(false, true, false);
        StructuralFlags roadOnly   = StructuralFlags.of(false, false, true);
        assertAll(
            () -> assertNotSame(StructuralFlags.NONE, zoningOnly,
                "non-empty zoningCount flips the flag-set out of NONE — industryZoned=true alone"),
            () -> assertTrue(zoningOnly.isAnySet(),
                "zoning-only flag-set has at least one flag set — the gate's permissive leg fires"),
            () -> assertFalse(zoningOnly.isEmpty(),
                "zoning-only flag-set is not the NONE sentinel"),
            () -> assertFalse(zoningOnly.isComplete(),
                "zoning-only flag-set is not the complete flag-set (corePopulated still real)"),
            () -> assertNotSame(StructuralFlags.NONE, roadOnly,
                "non-empty plannedRoads flips the flag-set out of NONE — roadLaid=true alone"),
            () -> assertTrue(roadOnly.isAnySet(),
                "road-only flag-set has at least one flag set — the gate's permissive leg fires"),
            () -> assertFalse(roadOnly.isEmpty(),
                "road-only flag-set is not the NONE sentinel"),
            () -> assertFalse(roadOnly.isComplete(),
                "road-only flag-set is not the complete flag-set (corePopulated still real)")
        );
    }

    @Test
    @DisplayName("core_populated true alone still flips the gate — the real derivation is the structural leg's third branch")
    void corePopulatedTrueAloneFlipsGate() {
        // The real derivation. core_populated is the only leg wired to a
        // real Town state today (the 32-block walk), and a town whose
        // core is fully built out qualifies for the act-4 gate even
        // without zoning or roads. The single-flag shape is the gate's
        // permissive floor — pin it explicitly so a regression that
        // accidentally AND-s the legs (treating any-set as the wrong
        // shape) fails this assertion.
        StructuralFlags coreOnly = StructuralFlags.of(true, false, false);
        assertAll(
            () -> assertTrue(coreOnly.corePopulated(),
                "core_populated is the real derivation — true here"),
            () -> assertFalse(coreOnly.industryZoned(),
                "industry_zoned is the empty-field floor — false here (zoningCount empty)"),
            () -> assertFalse(coreOnly.roadLaid(),
                "road_laid is the empty-field floor — false here (plannedRoads empty)"),
            () -> assertTrue(coreOnly.isAnySet(),
                "the partial flag-set has at least one flag set — the gate fires on a built-out core"),
            () -> assertFalse(coreOnly.isEmpty(),
                "the partial flag-set is not the NONE sentinel"),
            () -> assertFalse(coreOnly.isComplete(),
                "the partial flag-set is not the complete flag-set (the future strict predicate)")
        );
    }

    @Test
    @DisplayName("NONE is the act-4 gate's no-progress floor — no flag set, hubMode collapses to CONSTRUCTION regardless of acquisition")
    void noneIsGateNoProgressFloor() {
        // The third claim: NONE flag-set means hubMode() returns
        // CONSTRUCTION regardless of acquisition. The Town-level
        // hubMode() check is
        //   `a != FREE
        //    && structuralFlags().isAnySet()
        //    && meetsActThreshold(highestStanding(), ACT_THRESHOLD)`
        // — when isAnySet is false, the structural leg fails closed and
        // the gate collapses to CONSTRUCTION. The shape is pinned here
        // on the value-object side; the Town-level wiring follows from
        // the existing hubMode implementation.
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
