package org.lowern1ght.burg.domain.realm;

/**
 * How a town is held by a realm — the three kinds of member from the
 * realm design (realm README §"Decisions" 1 and 4, refined by the
 * 2026-07-31 grilling).
 *
 * <p>A realm is <em>not</em> a bag of equal villages. Its spine is the
 * {@link #METROPOLIS} (the first city) plus {@link #COLONY} satellites
 * founded by expedition — resource villages that depend on and trade
 * with the capital. Everything else is {@link #FOREIGN}: villages that
 * were never yours and attached through the three acquisition paths
 * (elevated / founded / captured). The two scales do not mix: a colony
 * is always founded, never captured.
 *
 * <p>This enum only names the kinds. What each kind may do is gated by
 * the town's {@link AutonomyBand}, not by this type.
 */
public enum HoldingKind {
    /** The first city — the realm's capital. Exactly one per realm. */
    METROPOLIS,
    /** A resource satellite founded by expedition; depends on the metropolis. */
    COLONY,
    /** A formerly outside village attached via elevation, founding, or capture. */
    FOREIGN;

    /**
     * True iff this holding belongs to the realm's spine — the
     * metropolis and its colony network that grow from inside
     * (VISION §"the realm grows from inside"), as opposed to the
     * periphery acquired from outside.
     */
    public boolean isHomeNetwork() {
        return this == METROPOLIS || this == COLONY;
    }
}
