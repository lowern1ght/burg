package org.lowern1ght.burg.behavior.diplomacy;

import org.lowern1ght.burg.town.Town;

/**
 * An alliance proposal: the {@code initiator} town asks the {@code target}
 * town to step up to {@link DiplomaticStatus#ALLY}.
 *
 * @param initiator the town proposing the alliance; never null
 * @param target the town being asked; never null
 *
 * <p>An alliance is the strongest diplomatic tie the engine models: shared
 * defence, joint projects, free movement. The engine's acceptance rule
 * (see {@code DiplomaticAI.shouldProposeAlliance}) is symmetric — both
 * sides must be in good standing (high morale) for a proposal to even
 * start, and a future acceptance step will require the target's consent.
 */
public record ProposeAllianceAction(Town initiator, Town target) implements DiplomaticAction {
    @Override
    public DiplomaticStatus proposedStatus() {
        return DiplomaticStatus.ALLY;
    }
}
