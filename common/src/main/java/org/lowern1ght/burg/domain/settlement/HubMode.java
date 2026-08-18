package org.lowern1ght.burg.domain.settlement;

/**
 * The two shapes the town hub can take (ADR-0019, change
 * {@code hub-becomes-window}). The legacy command-console hub and the
 * act-4+ window onto town intent are the same block with two widget
 * sets; this enum names them so the {@link HubView} can record which
 * one a town renders today.
 *
 * <p>{@link #CONSTRUCTION} is the additive default — every world saved
 * before this carve lands has its hub in CONSTRUCTION mode, and the
 * default {@code HubView.EMPTY} carries this mode for the same reason
 * {@link StockLedger#EMPTY} and {@link StandingBook#EMPTY} are
 * referentially-stable sentinels.
 *
 * <p>The mode is derived, not stored: the act-4 transition is the
 * predicate evaluated by {@code Town.hubView()} against the town's
 * current state. A town that crosses into {@link #SUPPLY} does not
 * flip back (ADR-0019 §"the transition is permanent"); a town whose
 * standing or structural predicate falls back to act-3 conditions
 * stays where it is, mode-wise.
 *
 * <p>No Minecraft imports. The enum lives in the settlement bounded
 * context alongside {@code Acquisition}, {@code StandingBook}, and
 * the rest of the strangler-side value objects (ADR-0008 §"Minecraft
 * types leave the domain").
 */
public enum HubMode {

    /**
     * Acts 0-3 hub: a command-console shape the player queues buildings
     * into. The default for every town that has not crossed the
     * act-4 transition yet.
     */
    CONSTRUCTION,

    /**
     * Act-4+ hub: a read-only window onto town intent. The player's
     * lever is supply, not orders. Reached only when the town's
     * derived transition predicate is met.
     */
    SUPPLY;

    /**
     * Returns the additive default. Mirrors {@link Acquisition#isDefault()}
     * — the legacy hub shape is what every pre-carve world has, so it
     * is what {@code HubView.EMPTY} carries.
     */
    public boolean isDefault() {
        return this == CONSTRUCTION;
    }
}