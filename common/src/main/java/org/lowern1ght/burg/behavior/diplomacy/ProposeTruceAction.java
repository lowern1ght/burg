package org.lowern1ght.burg.behavior.diplomacy;

import org.lowern1ght.burg.town.Town;

/**
 * A truce proposal: the {@code initiator} town asks the {@code target} town
 * to step down from {@link DiplomaticStatus#AT_WAR} to
 * {@link DiplomaticStatus#TRUCE}.
 *
 * @param initiator the town proposing the truce; never null
 * @param target the town being asked; never null
 *
 * <p>Truce is not peace — trade resumes but alliance-level cooperation
 * (shared projects, joint defence) does not. A future phase may promote a
 * standing truce into an alliance after enough ticks without incident.
 */
public record ProposeTruceAction(Town initiator, Town target) implements DiplomaticAction {
    @Override
    public DiplomaticStatus proposedStatus() {
        return DiplomaticStatus.TRUCE;
    }
}
