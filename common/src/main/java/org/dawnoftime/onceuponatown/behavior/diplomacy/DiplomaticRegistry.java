package org.dawnoftime.onceuponatown.behavior.diplomacy;

import org.dawnoftime.onceuponatown.town.Town;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The store of pairwise diplomatic relations for the engine scope.
 *
 * <p>Relations are kept in a nested map: an outer map keyed by the {@code from}
 * town, holding an inner map keyed by the {@code to} town. {@link #between}
 * reads the {@code a -> b} edge directly; if no edge exists, a fresh
 * {@link DiplomaticStatus#NEUTRAL} {@link Relation} is materialised on the
 * spot using the registry's current clock. The caller never sees a {@code null}.
 *
 * <p>{@link #update(Town, Town, DiplomaticStatus)} writes both directions in one
 * call so the registry stays symmetric: a war declared by {@code a} against
 * {@code b} is observed as {@code AT_WAR} from either side. {@link #allRelations}
 * returns a flat snapshot of every stored edge (both directions); a query that
 * needs symmetric results can dedupe by town-pair.
 *
 * <p>The clock is incremented by {@link #tickClock}; the engine calls this on
 * every server tick so a freshly-stamped relation carries the current tick,
 * not 0. The clock starts at 0 and only grows.
 */
public final class DiplomaticRegistry {

    private final Map<Town, Map<Town, Relation>> relations = new HashMap<>();
    private long clock = 0;

    /** Advance the clock by one tick. The engine calls this on every server tick. */
    public void tickClock() {
        clock++;
    }

    /** The clock value the next stamped relation will carry. */
    public long clock() {
        return clock;
    }

    /**
     * The relation from {@code a} to {@code b}, or a fresh NEUTRAL relation
     * (using the current clock) if no edge is recorded. Self-relations are an
     * error — a town is never its own diplomat.
     */
    public Relation between(Town a, Town b) {
        if (a.equals(b)) {
            throw new IllegalArgumentException("Towns must differ");
        }
        Map<Town, Relation> inner = relations.get(a);
        if (inner == null) {
            return Relation.neutral(a, b, clock);
        }
        Relation rel = inner.get(b);
        return rel != null ? rel : Relation.neutral(a, b, clock);
    }

    /**
     * Set the {@code a -> b} relation to {@code status} and mirror it in the
     * {@code b -> a} direction. Both edges carry the same clock value.
     */
    public void update(Town a, Town b, DiplomaticStatus status) {
        Relation ab = new Relation(a, b, status, clock);
        Relation ba = new Relation(b, a, status, clock);
        relations.computeIfAbsent(a, k -> new HashMap<>()).put(b, ab);
        relations.computeIfAbsent(b, k -> new HashMap<>()).put(a, ba);
    }

    /** Convenience: set the relation to {@link DiplomaticStatus#AT_WAR}. */
    public void declareWar(Town aggressor, Town defender) {
        update(aggressor, defender, DiplomaticStatus.AT_WAR);
    }

    /** Convenience: set the relation to {@link DiplomaticStatus#TRUCE}. */
    public void proposeTruce(Town a, Town b) {
        update(a, b, DiplomaticStatus.TRUCE);
    }

    /** Convenience: set the relation to {@link DiplomaticStatus#ALLY}. */
    public void proposeAlliance(Town a, Town b) {
        update(a, b, DiplomaticStatus.ALLY);
    }

    /**
     * Every stored relation in both directions. Newly-materialised NEUTRAL
     * edges from {@link #between} are NOT included — only edges someone has
     * explicitly written.
     */
    public List<Relation> allRelations() {
        List<Relation> all = new ArrayList<>();
        for (Map<Town, Relation> inner : relations.values()) {
            all.addAll(inner.values());
        }
        return all;
    }
}
