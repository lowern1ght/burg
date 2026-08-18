package org.lowern1ght.burg.application.settlement.ports;

import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.shared.CitizenId;

/**
 * The application layer's window onto a town's standing roll and
 * acquisition ladder (ADR-0014).
 *
 * <p>Use cases ({@code AdjustStanding}, future act-4/act-5 flows) depend on
 * this interface — never on {@code town/Town.java} — so they stay
 * Minecraft-free and unit-testable against an in-memory fake. The
 * infrastructure adapter ({@code TownStandingAdapter}) is the only
 * implementation that touches {@code Town}, converting {@link CitizenId} to
 * {@code UUID} at the edge (ADR-0008 §"Minecraft types leave the domain").
 *
 * <p>No Minecraft imports (modding/AGENT-RULES.md rule 6 — the fence covers
 * {@code application} ports as well as {@code domain/}).
 */
public interface TownStandingPort {

    /**
     * Returns the citizen's standing in this town. A citizen not on the roll
     * reads as {@link Standing#DEFAULT}, never as "absent".
     */
    Standing standingFor(CitizenId citizen);

    /**
     * Adds {@code delta} to the citizen's score. A score that falls back to
     * {@link Standing#DEFAULT} drops off the persisted roll.
     */
    void adjustStanding(CitizenId citizen, int delta);

    /** Returns the town's position on the acquisition ladder. */
    Acquisition acquisition();

    /**
     * Sets the town's acquisition. Callers are expected to advance
     * monotonically along the ladder ({@link Acquisition#precedes}); this
     * port does not gate the transition today.
     */
    void setAcquisition(Acquisition acquisition);
}
