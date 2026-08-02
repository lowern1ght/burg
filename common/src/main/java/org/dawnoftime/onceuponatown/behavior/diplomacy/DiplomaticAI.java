package org.dawnoftime.onceuponatown.behavior.diplomacy;

import org.dawnoftime.onceuponatown.behavior.morale.MoraleState;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.List;

/**
 * The decision layer that proposes diplomatic actions on a town's behalf.
 *
 * <p>The first-slice rules are deliberately simple and read only one input:
 * the average morale of the towns on either side. The engine gathers the
 * citizen lists (via {@code NpcSupplier.freeCitizens}) and passes them in.
 * Future slices will add distance, history, and the registry's prior
 * {@code Relation} for the same pair.
 *
 * <p>The morale thresholds below are placeholders — they will be re-tuned
 * once the engine runs end-to-end and a real village's mood curve is on
 * screen. The shape of the rule (war if much happier, truce if unhappy,
 * alliance if both happy) is the part that is meant to last.
 */
public final class DiplomaticAI {

    /** Aggressor is happy enough relative to the defender to declare war. */
    private static final float WAR_MORALE_ADVANTAGE = 20.0f;

    /** Own morale below this — wants peace, accepts a truce. */
    private static final float TRUCE_ACCEPT_BELOW = 40.0f;

    /** Both sides must clear this to consider an alliance. */
    private static final float ALLIANCE_MORALE_MIN = 60.0f;

    /**
     * Should {@code aggressor} declare war on {@code defender}?
     *
     * <p>Rule: yes if the aggressor's average morale exceeds the defender's
     * by {@link #WAR_MORALE_ADVANTAGE} or more. A town at the same morale as
     * its neighbour does not start a war; it takes a clear confidence edge.
     */
    public boolean shouldDeclareWar(MoraleState morale,
                                    List<Npc> aggressorCitizens,
                                    List<Npc> defenderCitizens) {
        float aggMorale = morale.averageForTown(aggressorCitizens);
        float defMorale = morale.averageForTown(defenderCitizens);
        return aggMorale > defMorale + WAR_MORALE_ADVANTAGE;
    }

    /**
     * Should this town accept a truce proposal?
     *
     * <p>Rule: yes if its own average morale is below
     * {@link #TRUCE_ACCEPT_BELOW}. A demoralised town wants peace; a town
     * with nothing to complain about keeps the war going.
     */
    public boolean shouldAcceptTruce(MoraleState morale, List<Npc> ownCitizens) {
        return morale.averageForTown(ownCitizens) < TRUCE_ACCEPT_BELOW;
    }

    /**
     * Should this town propose an alliance with the other?
     *
     * <p>Rule: yes only if both towns clear {@link #ALLIANCE_MORALE_MIN}.
     * An alliance needs two happy partners; one happy town proposing to a
     * grumpy one will be refused and leaves the registry cluttered.
     */
    public boolean shouldProposeAlliance(MoraleState morale,
                                         List<Npc> selfCitizens,
                                         List<Npc> otherCitizens) {
        float selfMorale = morale.averageForTown(selfCitizens);
        float otherMorale = morale.averageForTown(otherCitizens);
        return selfMorale > ALLIANCE_MORALE_MIN && otherMorale > ALLIANCE_MORALE_MIN;
    }
}
