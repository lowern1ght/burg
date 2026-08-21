package org.lowern1ght.burg.domain.realm;

import java.util.Locale;

/**
 * Where a held town sits on the autonomy–control slider
 * (VISION §"The autonomy–control slider").
 *
 * <p>The four bands are the slider's four named stops, in order of
 * increasing player control and decreasing village will:
 *
 * <blockquote><pre>
 * free ──────── elevated ──────── founded ──────── captured
 * (100% autonomous,          (loyal, takes        (resists, low morale,
 *  deaf to orders)            soft orders)          revolt risk, force only)
 * </pre></blockquote>
 *
 * <p>The bands are name-aligned with {@code Acquisition} in the
 * Settlement context ({@code domain.settlement.Acquisition}) by design:
 * a held foreign town's acquisition step maps one-to-one onto its
 * autonomy band. The mapping is by <em>name</em> at the edge
 * ({@link #fromAcquisitionName(String)}), not by a compile-time
 * dependency — Realm must not import Settlement (ADR-0008 §"Bounded
 * contexts": contexts talk through their edges, not each other's
 * internals).
 *
 * <p>This seed names the bands and the one gate the VISION text states
 * outright. Numeric autonomy drift over time (realm README §"Open
 * questions" #2) is deliberately not modelled — when it lands it will
 * either stay banded or become a float with these as cut-offs, and this
 * enum is the vocabulary either way.
 */
public enum AutonomyBand {
    /** 100% autonomous, deaf to orders. Pure pillar 1; the default for any unheld town. */
    FREE,
    /** Accepts the construction queue and directives; the NPC builder still paces itself. */
    ELEVATED,
    /** Accepts soft orders; loyal-with-age. Same command surface as {@link #ELEVATED}. */
    FOUNDED,
    /** Obeys only under garrison; starves and revolts if the garrison withdraws. */
    CAPTURED;

    /**
     * True iff orders to this town are refused outright (pillar 1 holds unqualified).
     *
     * @return {@code true} iff this band is {@link #FREE}
     */
    public boolean deafToOrders() {
        return this == FREE;
    }

    /**
     * True iff the town accepts the player's construction queue and
     * production directives — soft orders; the builder still sleeps,
     * still has morale (VISION: "elevated/founded villages accept the
     * player's construction queue and production directives").
     *
     * @return {@code true} iff this band is {@link #ELEVATED} or {@link #FOUNDED}
     */
    public boolean acceptsSoftOrders() {
        return this == ELEVATED || this == FOUNDED;
    }

    /**
     * True iff obedience runs on a garrison — the cost of the fast
     * path (VISION: "captured villages obey only under garrison").
     *
     * @return {@code true} iff this band is {@link #CAPTURED}
     */
    public boolean requiresGarrison() {
        return this == CAPTURED;
    }

    /**
     * Maps a persisted {@code Acquisition} name (Settlement context)
     * onto the band, defaulting to {@link #FREE} for null / empty /
     * unknown values. Name-based on purpose: no cross-context import,
     * and the forward-compat default matches the additive-save rule
     * the settlement carve established (ADR-0009).
     *
     * @param raw the persisted acquisition name (may be null or empty)
     * @return the matching {@link AutonomyBand}, or {@link #FREE} if unknown
     */
    public static AutonomyBand fromAcquisitionName(String raw) {
        if (raw == null || raw.isEmpty()) return FREE;
        try {
            return AutonomyBand.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return FREE;
        }
    }
}
