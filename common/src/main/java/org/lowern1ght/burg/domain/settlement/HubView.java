package org.lowern1ght.burg.domain.settlement;

import java.util.Objects;

/**
 * The town-level view of the hub's current mode — a thin domain wrapper
 * around a single {@link HubMode} that gives the {@code Town} facade a
 * referentially-stable handle to hand back from {@code Town#hubView()}.
 *
 * @param mode the hub mode (CONSTRUCTION or SUPPLY); never null
 *
 * <p>Today the view carries only the mode (acquisition + structural
 * predicates will fold into a richer value object in the act-4 PR that
 * also lands the supply-mode widget set). The record shape is kept so the
 * later additive expansion does not change the accessor's signature, and
 * so the {@link #EMPTY} sentinel follows the same referential-stability
 * recipe {@code StockLedger.EMPTY}, {@code ConstructionQueue.EMPTY}, and
 * {@code QuestLog.EMPTY} established (ADR-0010 / ADR-0011 / ADR-0012).
 *
 * <p>{@link #EMPTY} is the additive default — a fresh town reads
 * {@code HubView.EMPTY} (mode = {@link HubMode#CONSTRUCTION}). The
 * accessor is derived per call (no cache) while the predicate is a
 * one-liner over {@code constructionQueueView().isEmpty()} and
 * {@code getAcquisition()}; the cached-field discipline {@code stockLedger}
 * uses is reserved for the act-4 PR where the predicate stops being free.
 * (The construction queue was a cached field under
 * {@code constructionQueueDomain} until ADR-0027 promoted the domain
 * type to the SoT; the discipline that other dual-write fields use
 * still applies to {@code stockLedger} alone.)
 *
 * <p>No Minecraft imports. The view is built and consumed entirely on
 * the bare-JVM domain side; the engine edge ({@code TownAnchorBlock})
 * reads {@code Town#hubView()} only to log the mode at right-click.
 */
public record HubView(HubMode mode) {

    /**
     * Additive default for worlds saved before this carve and for any
     * town whose acquisition is FREE or whose queue is empty. Mode is
     * {@link HubMode#CONSTRUCTION} — the same shape the legacy hub
     * already renders, so old saves look unchanged.
     */
    public static final HubView EMPTY = new HubView(HubMode.CONSTRUCTION);

    public HubView {
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * Returns a view in the given mode. {@code null} mode and
     * {@link HubMode#CONSTRUCTION} both collapse to the {@link #EMPTY}
     * sentinel — the additive default. {@code CONSTRUCTION} being a
     * canonical equality case with {@code EMPTY}, the referential-
     * stability promise extends to it: callers that round-trip through
     * {@code of(...)} never produce a fresh record for the default mode.
     * Same discipline {@code StockLedger.EMPTY} uses (an empty ledger is
     * the canonical equality with the sentinel, and the add/take path
     * collapses back to it).
     */
    public static HubView of(HubMode mode) {
        return mode == null || mode == HubMode.CONSTRUCTION ? EMPTY : new HubView(mode);
    }

    /** True iff the hub is in {@link HubMode#CONSTRUCTION} mode. */
    public boolean isConstruction() {
        return mode == HubMode.CONSTRUCTION;
    }

    /** True iff the hub is in {@link HubMode#SUPPLY} mode. */
    public boolean isSupply() {
        return mode == HubMode.SUPPLY;
    }
}