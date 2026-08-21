package org.lowern1ght.burg.domain.settlement;

import java.util.Locale;
import java.util.Objects;

/**
 * Lifecycle of a town's relation to outside authority (ADR-0009 §"Acquisition
 * as a four-step state machine").
 *
 * <p>{@link #FREE} is the additive default for worlds saved before this
 * change: a town without a recorded acquisition is FREE, and FREE is the
 * loaded shape for an unknown NBT string. Transitions are produced by the
 * act-4 hub transition and the act-5 realm layer; this enum only names
 * them and persists them.
 *
 * <p>Order is meaningful — {@link #ordinal} is the progression along the
 * ladder ({@code FREE → ELEVATED → FOUNDED → CAPTURED}). A realm-level
 * caller is expected to monotonic-advance along this order; the
 * strangler facade on {@code Town} is not a gate today, but the enum
 * shape is chosen so a future validator can assert forward-only motion.
 */
public enum Acquisition {
    /** No claim asserted; the town governs itself. This is the additive default. */
    FREE,
    /** The town has noticed a chief; standing rules apply. */
    ELEVATED,
    /** A chief has been named and accepted. The hub turns into a window. */
    FOUNDED,
    /** A realm has taken the town. Standing bookkeeping continues under the conqueror. */
    CAPTURED;

    /**
     * Returns the Acquisition for a stored NBT string, or {@link #FREE} for
     * an absent / unrecognized value. Used at the Town facade edge when
     * additive NBT is missing or was written by an older build.
     *
     * @param raw the persisted NBT string (may be null or empty)
     * @return the matching {@link Acquisition}, or {@link #FREE} if unknown
     */
    public static Acquisition fromNbtOrDefault(String raw) {
        if (raw == null || raw.isEmpty()) return FREE;
        try {
            return Acquisition.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }

    /**
     * Stable NBT form — the {@link #name()} of the enum, uppercase.
     *
     * @return the uppercase {@link #name()} for persistence
     */
    public String toNbt() {
        return name();
    }

    /**
     * True iff this is the additive default for old saves.
     *
     * @return {@code true} iff this band is {@link #FREE}
     */
    public boolean isDefault() {
        return this == FREE;
    }

    /**
     * Monotonic-rank for callers that want to test progression.
     *
     * @return the {@link #ordinal()} along the FREE→CAPTURED ladder
     */
    public int rank() {
        return ordinal();
    }

    /**
     * True iff {@code other} is strictly later on the ladder.
     *
     * @param other the comparison target; never null
     * @return {@code true} iff this step is strictly earlier than {@code other}
     */
    public boolean precedes(Acquisition other) {
        Objects.requireNonNull(other, "other");
        return ordinal() < other.ordinal();
    }
}