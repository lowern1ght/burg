package org.lowern1ght.burg.domain.settlement.vanilla;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The vanilla house footprint value object, in pure JUnit.
 *
 * <p>Trivially small, but the XZ-distance helper is what
 * {@link VanillaBindingDecider} actually uses, and a regression in
 * {@code squaredXzDistanceTo} (e.g. accidentally including Y in the
 * distance, or sign-error on the delta) would silently mis-bucket the
 * meet-point decision for every anchor placed at a village.
 */
class VanillaHouseFootprintTest {

    @Test
    @DisplayName("two footprints are equal iff their X, Y and Z components match")
    void structuralEquality() {
        var a = new VanillaHouseFootprint(3, 64, -5);
        var b = new VanillaHouseFootprint(3, 64, -5);
        var c = new VanillaHouseFootprint(3, 64, -4);  // Z differs
        var d = new VanillaHouseFootprint(3, 65, -5);  // Y differs
        var e = new VanillaHouseFootprint(2, 64, -5);  // X differs
        assertAll(
            () -> assertEquals(a, b),
            () -> assertNotEquals(a, c, "Z delta must not equal"),
            () -> assertNotEquals(a, d, "Y delta must not equal"),
            () -> assertNotEquals(a, e, "X delta must not equal")
        );
    }

    @Test
    @DisplayName("XZ distance ignores Y entirely")
    void squaredXzDistanceIgnoresY() {
        var lowY = new VanillaHouseFootprint(0, 0, 0);
        var highY = new VanillaHouseFootprint(0, 200, 0);
        assertEquals(lowY.squaredXzDistanceTo(0, 0),
            highY.squaredXzDistanceTo(0, 0),
            "Y must not enter the squared XZ distance");
    }

    @Test
    @DisplayName("XZ distance is the standard Pythagorean triple")
    void squaredXzDistanceMatchesPythagoras() {
        var fp = new VanillaHouseFootprint(3, 64, 4);
        // (3-0)^2 + (4-0)^2 = 9 + 16 = 25
        assertEquals(25, fp.squaredXzDistanceTo(0, 0));
        // (-3)^2 + (-4)^2 = same
        assertEquals(25, fp.squaredXzDistanceTo(6, 8));
    }
}