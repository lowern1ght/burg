package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The two-dimensional integer point — value-type contract: equality,
 * translation, sub-traction. No {@code net.minecraft} import, so the
 * test runs on a bare JVM alongside the rest of the engine's tests.
 */
class PointTest {

    @Test
    @DisplayName("Point.ZERO is the canvas origin")
    void zeroIsTheOrigin() {
        assertEquals(new Point(0, 0), Point.ZERO);
        assertEquals(0, Point.ZERO.x());
        assertEquals(0, Point.ZERO.y());
    }

    @Test
    @DisplayName("add translates by another point")
    void addPoint() {
        Point p = new Point(10, 20);
        assertEquals(new Point(15, 25), p.add(new Point(5, 5)));
    }

    @Test
    @DisplayName("add translates by a delta pair")
    void addDelta() {
        assertEquals(new Point(12, 22), new Point(10, 20).add(2, 2));
        assertEquals(new Point(-3, 7), new Point(-5, 5).add(2, 2));
    }

    @Test
    @DisplayName("sub subtracts another point")
    void subPoint() {
        Point p = new Point(10, 20);
        assertEquals(new Point(5, 15), p.sub(new Point(5, 5)));
    }

    @Test
    @DisplayName("sub subtracts a delta pair")
    void subDelta() {
        assertEquals(new Point(8, 18), new Point(10, 20).sub(2, 2));
        assertEquals(new Point(-7, 3), new Point(-5, 5).sub(2, 2));
    }

    @Test
    @DisplayName("translation is non-mutating")
    void addIsImmutable() {
        Point original = new Point(3, 4);
        Point translated = original.add(new Point(1, 1));
        assertAll(
            () -> assertEquals(new Point(3, 4), original, "original unchanged"),
            () -> assertEquals(new Point(4, 5), translated),
            () -> assertNotEquals(original, translated)
        );
    }

    @Test
    @DisplayName("equality follows the two components")
    void equalityIsComponentwise() {
        Point a = new Point(7, 11);
        Point b = new Point(7, 11);
        Point c = new Point(11, 7);
        assertAll(
            () -> assertEquals(a, b, "same components → equal"),
            () -> assertEquals(a.hashCode(), b.hashCode(), "equal hashCodes"),
            () -> assertNotEquals(a, c, "swapped components → not equal")
        );
    }

    @Test
    @DisplayName("null translation is rejected")
    void nullGuard() {
        Point p = new Point(1, 1);
        assertThrows(NullPointerException.class, () -> p.add(null));
        assertThrows(NullPointerException.class, () -> p.sub(null));
    }
}