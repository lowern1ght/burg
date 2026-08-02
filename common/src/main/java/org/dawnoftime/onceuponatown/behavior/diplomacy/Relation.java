package org.dawnoftime.onceuponatown.behavior.diplomacy;

import org.dawnoftime.onceuponatown.town.Town;

/**
 * One direction of a bilateral relation between two towns.
 *
 * <p>A relation is stored as a directed edge ({@code from -> to}). The
 * {@link DiplomaticRegistry} keeps the reverse edge in sync, so a
 * {@code between(a, b)} lookup returns the same {@link DiplomaticStatus}
 * regardless of argument order, but each side remembers when it last
 * re-stated its position (the {@code lastUpdated} clock).
 *
 * <p>Records validate their components in the compact constructor: both
 * towns must be non-null and the clock must be non-negative. This catches
 * construction-site bugs (passing a {@code null} town by accident) before
 * they propagate into the registry's nested map.
 */
public record Relation(
    Town from,
    Town to,
    DiplomaticStatus status,
    long lastUpdated
) {
    public Relation {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Towns must be non-null");
        }
        if (lastUpdated < 0) {
            throw new IllegalArgumentException("lastUpdated must be non-negative");
        }
    }

    /** Build a NEUTRAL relation with the registry's current clock value. */
    public static Relation neutral(Town a, Town b, long now) {
        return new Relation(a, b, DiplomaticStatus.NEUTRAL, now);
    }
}
