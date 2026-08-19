package org.lowern1ght.burg.domain.settlement.vanilla;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The vanilla-village binding decision function, in pure JUnit.
 *
 * <p>Three correctness traps this test set is explicitly here to catch:
 *
 * <ol>
 *   <li>An empty footprint set MUST read as "skip, no vanilla footprints"
 *       — this is the user-visible {@code bindToVanillaVillage returns false
 *       on non-vanilla coords} contract, and a regression that flipped it
 *       to {@code Bind} would place a bridgehead piece over open plains
 *       every time a player set an anchor anywhere.</li>
 *   <li>A non-empty footprint set whose nearest footprint is further than
 *       the radius MUST also read as Skip — placing the anchor at the
 *       edge of a different vanilla village's reach should not silently
 *       bind it to the wrong village.</li>
 *   <li>A non-empty footprint set with at least one footprint inside the
 *       radius MUST read as {@link VanillaBindingDecision.Bind Bind}
 *       carrying the full footprint set, so the {@code Town.bindToVanillaVillage}
 *       facade can register every existing vanilla house as a blocked
 *       zone and never grow a Burg building on top of one.</li>
 * </ol>
 *
 * <p>Bare JVM, no Minecraft imports. The {@code BlockPos} /
 * {@code ServerLevel} arguments of {@code Town.bindToVanillaVillage}
 * are unwrapped at the {@code Town} facade edge to {@code (int, int)}
 * coordinates before the decider is called; this test exercises the
 * decider directly with those primitive arguments.
 */
class VanillaBindingDeciderTest {

    private static VanillaHouseFootprint fp(int x, int z) {
        return new VanillaHouseFootprint(x, 64, z);
    }

    @Test
    @DisplayName("an empty footprint set is the no-vanilla-footprints Skip")
    void emptyFootprintsSkip() {
        VanillaBindingDecider decider = new VanillaBindingDecider();
        VanillaBindingDecision decision = decider.decide(Set.of(), 0, 0);
        assertAll(
            () -> assertTrue(decision instanceof VanillaBindingDecision.Skip,
                "an empty footprint set must Skip"),
            () -> assertSame(VanillaBindingDecision.Skip.REASON_NO_FOOTPRINTS,
                decision.reasonCode(),
                "the reason is exactly REASON_NO_FOOTPRINTS")
        );
    }

    @Test
    @DisplayName("a candidate with no nearby footprint is the out-of-range Skip")
    void outOfRangeSkip() {
        VanillaBindingDecider decider = new VanillaBindingDecider();
        // Three footprints, all at least 64 blocks away on XZ from (0, 0).
        Set<VanillaHouseFootprint> farAway = Set.of(fp(100, 100), fp(-100, 100), fp(100, -100));
        VanillaBindingDecision decision = decider.decide(farAway, 0, 0);
        assertTrue(decision instanceof VanillaBindingDecision.Skip,
            "footprints further than the radius must Skip");
        assertEquals(VanillaBindingDecision.Skip.REASON_OUT_OF_RANGE, decision.reasonCode(),
            "the reason is exactly REASON_OUT_OF_RANGE");
    }

    @Test
    @DisplayName("a candidate at the meeting point of an existing village binds")
    void candidateInsideBinds() {
        VanillaBindingDecider decider = new VanillaBindingDecider();
        // Vanilla plains village of six houses, centred around (0, 0).
        Set<VanillaHouseFootprint> village = Set.of(
            fp(8, 0), fp(-8, 0), fp(0, 8), fp(0, -8), fp(12, 12), fp(-12, -12));
        VanillaBindingDecision decision = decider.decide(village, 0, 0);
        assertTrue(decision instanceof VanillaBindingDecision.Bind,
            "a candidate within 32 XZ-blocks of a footprint must Bind");
        @SuppressWarnings("unchecked")
        var bind = (VanillaBindingDecision.Bind) decision;
        assertEquals(village, bind.footprints(),
            "the Bind carries the full footprint set, so the Town facade can block them all");
    }

    @Test
    @DisplayName("the default radius is 32 — vanilla's own village radius")
    void defaultRadiusIsThirtyTwo() {
        VanillaBindingDecider decider = new VanillaBindingDecider();
        // 32 XZ-blocks exactly: at the boundary, still inside the radius.
        Set<VanillaHouseFootprint> edge = Set.of(fp(0, 0), fp(32, 0));
        assertTrue(decider.decide(edge, 0, 0) instanceof VanillaBindingDecision.Bind,
            "32 blocks is still within the radius");
        // 33 XZ-blocks: just outside, falls back to Skip.
        Set<VanillaHouseFootprint> justOutside = Set.of(fp(0, 0), fp(33, 0));
        assertTrue(decider.decide(justOutside, 0, 0) instanceof VanillaBindingDecision.Skip,
            "33 blocks is outside the radius");
        assertEquals(VanillaBindingDecider.DEFAULT_RADIUS, decider.radius());
    }

    @Test
    @DisplayName("the radius constructor rejects non-positive values")
    void radiusConstructorRejectsNonPositive() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new VanillaBindingDecider(0),
                "zero is not a radius"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new VanillaBindingDecider(-1),
                "negative is not a radius")
        );
    }

    @Test
    @DisplayName("Bind refuses an empty footprint set at the type level")
    void bindRejectsEmptyFootprints() {
        assertThrows(IllegalArgumentException.class,
            () -> new VanillaBindingDecision.Bind(Set.of()),
            "a Bind with no footprints would silently register zero blocked zones");
    }
}