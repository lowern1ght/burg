package org.lowern1ght.burg.behavior.diplomacy;

import org.lowern1ght.burg.town.Town;

/**
 * A war declaration: the {@code initiator} town formalises
 * {@link DiplomaticStatus#AT_WAR} with the {@code target} town.
 *
 * @param initiator the town declaring war; never null
 * @param target the town being declared on; never null
 *
 * <p>War is immediate on acceptance — there is no "declared, awaiting response"
 * state in this slice. A later phase may add a {@code WARMONGERING} status
 * for the window between declaration and the first shot, when the declaration
 * can still be rescinded without consequence.
 */
public record DeclareWarAction(Town initiator, Town target) implements DiplomaticAction {
    @Override
    public DiplomaticStatus proposedStatus() {
        return DiplomaticStatus.AT_WAR;
    }
}
