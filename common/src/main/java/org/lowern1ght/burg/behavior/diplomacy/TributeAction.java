package org.lowern1ght.burg.behavior.diplomacy;

import org.lowern1ght.burg.town.Town;

/**
 * An ongoing tribute payment: the {@code initiator} town pays the
 * {@code target} town {@code amountPerTick} of its production each tick.
 *
 * <p>Tribute is an asymmetric arrangement and does not change the diplomatic
 * status — {@link #proposedStatus()} returns {@link DiplomaticStatus#NEUTRAL}.
 * A town can pay tribute to a rival it is at war with (extortion in
 * reverse), to an ally it owes a debt to, or to a neutral town it is trying
 * to keep on its side. The status column is independent of the ledger.
 */
public record TributeAction(Town initiator, Town target, int amountPerTick) implements DiplomaticAction {
    @Override
    public DiplomaticStatus proposedStatus() {
        return DiplomaticStatus.NEUTRAL;
    }
}
