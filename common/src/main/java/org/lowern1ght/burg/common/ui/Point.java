package org.lowern1ght.burg.common.ui;

import java.util.Objects;

/**
 * A two-dimensional integer point — the engine's bare-JVM {@code x, y} pair.
 * Immutable value type. Equality follows the two components.
 *
 * <p>No {@code net.minecraft} import. The whole {@code common.ui} package is
 * deliberately Minecraft-free so the engine can be unit-tested on a bare JVM
 * and so the {@code DomainPurityTest} import fence can extend cleanly into
 * the UI types (ADR-0022).
 *
 * <p>The four {@code add} / {@code sub} methods return a new instance — this
 * is a value type, never mutable. Overflow is not detected: the engine only
 * does pixel arithmetic that stays well inside the {@code int} band.
 */
public record Point(int x, int y) {

    /** {@code (0, 0)} — the canvas origin. */
    public static final Point ZERO = new Point(0, 0);

    public Point {
        // record; defensive null check is not applicable to int primitives.
    }

    /** Returns a new point translated by {@code other}. */
    public Point add(Point other) {
        Objects.requireNonNull(other, "other");
        return new Point(x + other.x, y + other.y);
    }

    /** Returns a new point translated by the given delta. */
    public Point add(int dx, int dy) {
        return new Point(x + dx, y + dy);
    }

    /** Returns a new point with {@code other} subtracted. */
    public Point sub(Point other) {
        Objects.requireNonNull(other, "other");
        return new Point(x - other.x, y - other.y);
    }

    /** Returns a new point with the given delta subtracted. */
    public Point sub(int dx, int dy) {
        return new Point(x - dx, y - dy);
    }
}