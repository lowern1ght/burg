package org.lowern1ght.burg.common.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Axis-aligned integer rectangle — {@link Rect} value-type contract:
 * emptiness, contains, inset, intersection, translate. The rectangle is
 * the engine's layout primitive; every Widget carries one. No Minecraft
 * imports — pure POJO test.
 */
class RectTest {

    @Test
    @DisplayName("Rect.EMPTY is the canonical empty rect")
    void emptyIsCanonical() {
        assertAll(
            () -> assertTrue(Rect.EMPTY.isEmpty()),
            () -> assertEquals(0, Rect.EMPTY.w()),
            () -> assertEquals(0, Rect.EMPTY.h())
        );
    }

    @Test
    @DisplayName("rects with non-positive width or height are empty")
    void emptyPredicate() {
        assertAll(
            () -> assertTrue(new Rect(0, 0, 0, 5).isEmpty(), "zero width is empty"),
            () -> assertTrue(new Rect(0, 0, 5, 0).isEmpty(), "zero height is empty"),
            () -> assertTrue(new Rect(0, 0, -3, 5).isEmpty(), "negative width is empty"),
            () -> assertTrue(new Rect(0, 0, 5, -2).isEmpty(), "negative height is empty"),
            () -> assertFalse(new Rect(0, 0, 1, 1).isEmpty())
        );
    }

    @Test
    @DisplayName("contains is half-open on the bottom-right edge")
    void containsIsHalfOpen() {
        Rect r = new Rect(10, 10, 20, 20);
        assertAll(
            () -> assertTrue(r.contains(new Point(10, 10)), "top-left corner is inside"),
            () -> assertTrue(r.contains(new Point(15, 15)), "centre is inside"),
            () -> assertTrue(r.contains(new Point(29, 15)), "x == 29 is INSIDE (half-open, x < 30)"),
            () -> assertTrue(r.contains(new Point(15, 29)), "y == 29 is INSIDE (half-open, y < 30)"),
            () -> assertFalse(r.contains(new Point(30, 30)), "bottom-right corner is OUTSIDE (half-open)"),
            () -> assertFalse(r.contains(new Point(15, 30)), "y == 30 is OUTSIDE"),
            () -> assertFalse(r.contains(new Point(30, 15)), "x == 30 is OUTSIDE"),
            () -> assertFalse(r.contains(new Point(5, 15)), "left of origin is outside")
        );
    }

    @Test
    @DisplayName("empty rects contain no points")
    void emptyContainsNothing() {
        Rect empty = new Rect(0, 0, 0, 0);
        assertFalse(empty.contains(new Point(0, 0)));
        assertFalse(empty.contains(new Point(-5, -5)));
    }

    @Test
    @DisplayName("inset shrinks symmetrically; negative inset expands")
    void inset() {
        Rect r = new Rect(10, 10, 40, 20);
        assertAll(
            () -> assertEquals(new Rect(12, 12, 36, 16), r.inset(2)),
            () -> assertEquals(r, r.inset(0), "inset(0) is identity"),
            () -> assertEquals(new Rect(0, 0, 60, 40), r.inset(-10), "negative inset expands")
        );
    }

    @Test
    @DisplayName("inset past the edges clamps to zero")
    void insetClamps() {
        Rect r = new Rect(0, 0, 4, 4);
        assertEquals(new Rect(2, 2, 0, 0), r.inset(2));
        assertEquals(new Rect(10, 10, 0, 0), r.inset(10), "clamps to a 0x0 rect, not negative");
    }

    @Test
    @DisplayName("intersection returns the overlap rect")
    void intersection() {
        Rect a = new Rect(0, 0, 10, 10);
        Rect b = new Rect(5, 5, 10, 10);
        assertEquals(new Rect(5, 5, 5, 5), a.intersection(b));
    }

    @Test
    @DisplayName("intersection of disjoint rects is EMPTY")
    void intersectionDisjoint() {
        Rect a = new Rect(0, 0, 5, 5);
        Rect b = new Rect(10, 10, 5, 5);
        assertSame(Rect.EMPTY, a.intersection(b));
    }

    @Test
    @DisplayName("intersection with an empty rect is EMPTY")
    void intersectionWithEmpty() {
        Rect a = new Rect(0, 0, 5, 5);
        Rect empty = new Rect(0, 0, 0, 0);
        assertSame(Rect.EMPTY, a.intersection(empty));
        assertSame(Rect.EMPTY, empty.intersection(a));
    }

    @Test
    @DisplayName("translate moves the origin; dimensions are unchanged")
    void translate() {
        Rect r = new Rect(10, 10, 20, 20);
        assertAll(
            () -> assertEquals(new Rect(15, 20, 20, 20), r.translate(5, 10)),
            () -> assertEquals(new Rect(15, 20, 20, 20), r.translate(new Point(5, 10))),
            () -> assertEquals(r, r.translate(0, 0))
        );
    }

    @Test
    @DisplayName("rect equality follows the four components")
    void equality() {
        assertEquals(new Rect(1, 2, 3, 4), new Rect(1, 2, 3, 4));
        assertEquals(new Rect(1, 2, 3, 4).hashCode(), new Rect(1, 2, 3, 4).hashCode());
        assertNotEquals(new Rect(1, 2, 3, 4), new Rect(1, 2, 3, 5));
        assertNotEquals(new Rect(1, 2, 3, 4), new Rect(0, 2, 3, 4));
    }
}