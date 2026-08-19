package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * An axis-aligned integer rectangle: {@code (x, y)} top-left + {@code (w, h)}.
 * The engine's layout primitive — every {@link Widget} carries one.
 *
 * <p>No {@code net.minecraft} import. {@link Rect} is a bare-JVM value type
 * the UI tests can construct on any JVM (ADR-0022 §"Three rules").
 *
 * <p>An empty rectangle is one whose width or height is non-positive.
 * Layouts may produce empty rects at the edges (e.g. a container whose
 * first child is wider than its parent); hit-test, draw, and intersection
 * all treat an empty rect as a no-op.
 */
public record Rect(int x, int y, int w, int h) {

    public Rect {
        // record; primitives don't need a null check.
    }

    /**
     * A rect at the origin with zero size — the "nothing yet" sentinel.
     */
    public static final Rect EMPTY = new Rect(0, 0, 0, 0);

    /** Returns true iff {@code w <= 0} or {@code h <= 0}. */
    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    /** Returns true iff {@code point} lies inside this rect (inclusive). */
    public boolean contains(Point point) {
        Objects.requireNonNull(point, "point");
        return !isEmpty()
            && point.x() >= x
            && point.x() < x + w
            && point.y() >= y
            && point.y() < y + h;
    }

    /**
     * Returns a new rect inset by {@code n} on all four sides. A negative
     * {@code n} expands. {@code width} and {@code height} clamp to zero.
     */
    public Rect inset(int n) {
        return new Rect(x + n, y + n, Math.max(0, w - 2 * n), Math.max(0, h - 2 * n));
    }

    /**
     * Returns the intersection of this rect with {@code other}. If the two
     * do not overlap, the result is {@link #EMPTY} (w = h = 0).
     */
    public Rect intersection(Rect other) {
        Objects.requireNonNull(other, "other");
        if (isEmpty() || other.isEmpty()) return EMPTY;
        int x0 = Math.max(x, other.x);
        int y0 = Math.max(y, other.y);
        int x1 = Math.min(x + w, other.x + other.w);
        int y1 = Math.min(y + h, other.y + other.h);
        int iw = x1 - x0;
        int ih = y1 - y0;
        return iw <= 0 || ih <= 0 ? EMPTY : new Rect(x0, y0, iw, ih);
    }

    /**
     * Returns a new rect translated by {@code (dx, dy)}. Translation never
     * produces a negative dimension; only the origin moves.
     */
    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, w, h);
    }

    /**
     * Returns a new rect translated by {@code point}. Same shape as
     * {@link #translate(int, int)}; convenience for code that already has
     * a {@link Point} in hand.
     */
    public Rect translate(Point point) {
        Objects.requireNonNull(point, "point");
        return translate(point.x(), point.y());
    }
}