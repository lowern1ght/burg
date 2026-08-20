package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The full per-town standing book — one entry per citizen that has ever
 * stood in this town. Read-only externally; mutators return new books so
 * the {@code Town} facade can hand out a thread-safe snapshot and never
 * need a copy-on-write wrapper.
 *
 * <p>Empty books are the additive default for old saves — a {@code Town}
 * loaded from pre-carve NBT has no {@code Standings} key, and
 * {@code Town.fromNbt} constructs it with an empty book. Zero-score entries
 * are dropped at the edge so the persisted NBT stays sparse: only citizens
 * who have actually accumulated standing appear in the list.
 *
 * <p>No Minecraft imports; {@link CitizenId} is the value-object wrapper
 * (ADR-0008).
 */
public final class StandingBook {

    /** Empty book — the additive default for worlds saved before this change. */
    public static final StandingBook EMPTY = new StandingBook(Map.of());

    private final Map<CitizenId, Standing> entries;

    private StandingBook(Map<CitizenId, Standing> entries) {
        this.entries = entries;
    }

    /**
     * Returns the standing for a citizen, or {@link Standing#ZERO} if the
     * citizen is not on the roll. The roll is sparse: a citizen not yet seen
     * by this town reads as zero, never as "absent".
     */
    public Standing standingFor(CitizenId citizen) {
        Objects.requireNonNull(citizen, "citizen");
        Standing s = entries.get(citizen);
        return s != null ? s : new Standing(citizen, Standing.DEFAULT);
    }

    /**
     * Returns a new book with the score for {@code citizen} set to
     * {@code newValue}. Citizens whose score is set to {@link Standing#DEFAULT}
     * are dropped from the book so the persisted form stays sparse.
     */
    public StandingBook set(CitizenId citizen, int newValue) {
        Objects.requireNonNull(citizen, "citizen");
        Map<CitizenId, Standing> next = new LinkedHashMap<>(entries);
        if (newValue == Standing.DEFAULT) {
            next.remove(citizen);
        } else {
            next.put(citizen, new Standing(citizen, newValue));
        }
        return next.isEmpty() ? EMPTY : new StandingBook(next);
    }

    /**
     * Returns a new book with {@code delta} added to the score for
     * {@code citizen}. Citizens whose score falls back to
     * {@link Standing#DEFAULT} after the adjustment are dropped.
     */
    public StandingBook adjust(CitizenId citizen, int delta) {
        Objects.requireNonNull(citizen, "citizen");
        int current = entries.containsKey(citizen) ? entries.get(citizen).value() : Standing.DEFAULT;
        return set(citizen, current + delta);
    }

    /** True iff no citizen has ever accumulated standing. */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Number of distinct citizens on the roll. */
    public int size() {
        return entries.size();
    }

    /** Read-only view of every entry. Order is insertion order — the order the book was built. */
    public Map<CitizenId, Standing> entries() {
        return Collections.unmodifiableMap(entries);
    }

    /**
     * The highest standing score on the roll, or {@link Standing#DEFAULT}
     * (zero) when the book is empty. The "best standing any citizen has
     * earned" reading — used by {@code Town.hubMode()} for the act-4
     * gate's third leg: the town crosses {@code BurgConfig.ACT_THRESHOLD}
     * when at least one citizen's standing has. An empty book reads as
     * zero, never as "absent" — the same additive discipline
     * {@link #standingFor(CitizenId)} applies to a single citizen.
     *
     * <p>Single linear scan; the roll is sparse (a town with a few dozen
     * citizens and a handful of non-zero entries is the steady state) so
     * the O(N) cost is bounded and the per-call cache discipline
     * {@link #standingFor(CitizenId)} uses is not worth duplicating here.
     */
    public int highestStanding() {
        int max = Standing.DEFAULT;
        for (Standing s : entries.values()) {
            if (s.value() > max) max = s.value();
        }
        return max;
    }

    /**
     * Static helper for the third leg of the act-4 hub-mode gate:
     * {@code standing >= threshold}. Lives here (not on {@code Town}) so
     * the bare-JVM test classpath can exercise it without constructing a
     * {@code Town} (which references {@code net.minecraft.*} on its
     * god-object fields and cannot be loaded by the bare-JVM test
     * classpath). The {@code Town}-side {@code hubMode()} method
     * delegates; this is the one truth.
     *
     * <p>The {@code int} standing parameter accepts the result of
     * {@link #highestStanding()} (or any other standing reading — the
     * helper is content-free about its source). The {@code double}
     * threshold matches the {@code BurgConfig.ACT_THRESHOLD} spec's
     * {@code DoubleValue} type.
     */
    public static boolean meetsActThreshold(int standing, double threshold) {
        return standing >= threshold;
    }

    /** Builds an empty book. */
    public static StandingBook empty() {
        return EMPTY;
    }

    /** Builds a book from an existing map. The map is defensively copied. */
    public static StandingBook of(Map<CitizenId, Standing> source) {
        Objects.requireNonNull(source, "source");
        if (source.isEmpty()) return EMPTY;
        Map<CitizenId, Standing> copy = new LinkedHashMap<>(source);
        // Drop any zero entries — they shouldn't be here, but the constructor
        // is the last line of defence.
        copy.values().removeIf(standing -> standing.value() == Standing.DEFAULT);
        return copy.isEmpty() ? EMPTY : new StandingBook(copy);
    }
}