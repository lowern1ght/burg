package org.lowern1ght.burg.domain.settlement;

import java.util.Objects;

/**
 * A town's hub view: the current {@link HubMode} the renderer should
 * pick when the player opens the Town Anchor, plus an "empty" sentinel
 * for towns whose hub carries no content right now (ADR-0019,
 * change {@code hub-becomes-window}).
 *
 * <p>The view is a thin record today — its first job is to name the
 * mode and to expose the {@link #isEmpty()} predicate that the
 * {@code Town#hubView()} accessor uses to short-circuit the GUI. The
 * richer content (read-only intent list, stock-gap list, supply widget
 * set) lands in a future carve that depends on this one: a future
 * carve adds the SUPPLY-mode widgets to {@code TownHubScreen} and
 * reads the inventory + intent fields this record will then carry.
 *
 * <p>Empty when either the construction queue is empty (the town has
 * nothing for the player to influence) or the acquisition ladder
 * position is outside the act-4 set {@code {ELEVATED, FOUNDED}}. In
 * the empty state the renderer falls back to the legacy command-
 * console shape — which, in the additive carve, means today's screen
 * rendered unchanged. The first version carries one field
 * ({@link #mode}); the full SUPPLY-mode content lands with the GUI
 * carve.
 *
 * <p>No Minecraft imports. The record lives in the settlement bounded
 * context and is the domain-side analogue of the legacy
 * {@code town.TownHubDataBuilder} (which assembles the MC-tag hub
 * data the client renders). The two are unrelated on disk: the
 * HubView does not appear in NBT (it is derived; a server with this
 * change and an old world needs no migration).
 */
public record HubView(HubMode mode) {

    /**
     * The additive default: a town with no construction queue or an
     * acquisition outside the act-4 set. Referentially stable so
     * {@code Town#hubView()} can short-circuit on the empty path.
     */
    public static final HubView EMPTY = new HubView(HubMode.CONSTRUCTION);

    public HubView {
        Objects.requireNonNull(mode, "mode");
    }

    /**
     * True iff this view carries no content for the renderer. The empty
     * state is the additive default — every world saved before this
     * carve reads as empty, and a town whose construction queue is
     * drained reads as empty even mid-game.
     */
    public boolean isEmpty() {
        return this == EMPTY;
    }
}