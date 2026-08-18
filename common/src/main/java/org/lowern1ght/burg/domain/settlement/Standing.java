package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.Objects;

/**
 * One citizen's standing in one town: a citizen identifier and an integer
 * score. Immutable; mutations are produced by {@link #withDelta(int)} and
 * applied to a {@link StandingBook} entry.
 *
 * <p>The score is intentionally an {@code int}, not a bucketed enum: the
 * {@code earned-crown-trajectory} (VISION.md) and the act-4 standing
 * threshold (50 in the shipped builder datapack) are continuous numbers,
 * and the bucketed reading is the only place an enum belongs. Today that
 * reading lives on {@code MoraleLevel} in {@code behavior.morale}; the
 * two are not merged because {@code MoraleLevel} is town-morale (a town
 * property), whereas {@code Standing} is a per-citizen relationship.
 *
 * <p>No Minecraft imports. The citizen identifier is a {@link CitizenId}
 * value object (ADR-0008 §"Minecraft types leave the domain").
 */
public record Standing(CitizenId citizen, int value) {

    /** Additive default for old saves — a brand-new entry is zero. */
    public static final int DEFAULT = 0;

    /** Citizens not on the roll read as zero; this constant centralizes that. */
    public static final Standing ZERO = new Standing(CitizenId.EMPTY, DEFAULT);

    public Standing {
        Objects.requireNonNull(citizen, "citizen");
    }

    /** Returns a new {@code Standing} with {@code delta} added to the score; the citizen is unchanged. */
    public Standing withDelta(int delta) {
        return new Standing(citizen, value + delta);
    }

    /** Returns a new {@code Standing} with the score set to {@code newValue}; the citizen is unchanged. */
    public Standing withValue(int newValue) {
        return new Standing(citizen, newValue);
    }

    /** True iff this entry has a zero score and would not be persisted. */
    public boolean isZero() {
        return value == DEFAULT;
    }
}