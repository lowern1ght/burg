package org.dawnoftime.onceuponatown.behavior.diplomacy;

import org.dawnoftime.onceuponatown.town.Town;

/**
 * A diplomatic action proposed by one town and aimed at another.
 *
 * <p>The sealed hierarchy pins down the four actions the engine cares about
 * today: a war declaration, a truce proposal, an alliance proposal, and a
 * tribute payment. New action shapes (open border, declare feud, surrender)
 * land as new permits, and any {@code switch} over the type will fail to
 * compile until it is updated — which is the whole point of the seal.
 *
 * <p>{@link #proposedStatus()} answers "what status would this action set on
 * the registry if accepted?" {@link TributeAction} returns
 * {@link DiplomaticStatus#NEUTRAL} — tribute is a payment, not a status
 * change. The status column stays where it was.
 */
public sealed interface DiplomaticAction
    permits DeclareWarAction, ProposeTruceAction, ProposeAllianceAction, TributeAction {

    Town initiator();

    Town target();

    DiplomaticStatus proposedStatus();
}
