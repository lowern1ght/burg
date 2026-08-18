package org.lowern1ght.burg.domain.war;

import java.util.OptionalInt;

/**
 * The resolved result of one war-scale engagement: who prevailed, and
 * what it cost each side.
 *
 * <p>Thin seed (ADR-0017). The in-game engine — {@code Squad},
 * {@code BattleStateMachine}, {@code CasualtyModel} in
 * {@code behavior.war} — stays exactly where it is; this record is the
 * Minecraft-free summary a realm-level caller (auto-resolve, campaign
 * log, diplomacy's war/truce decisions) will consume. A future adapter
 * at the behavior edge folds {@code CasualtyModel} totals into the
 * casualty counts; nothing in this type knows how a battle is fought,
 * only how one reads afterwards.
 *
 * <p>The casualty counts are {@link OptionalInt}: an auto-resolved
 * engagement may know the winner without bookkeeping bodies, and a
 * caller that never reads counts must not be forced to invent them.
 * When counts <em>are</em> present they are non-negative — negative
 * casualties are a construction-site bug, caught here rather than
 * persisted.
 *
 * @param attackerWins       true iff the attacking side prevailed
 * @param attackerCasualties dead-and-injured toll on the attacking side, if counted
 * @param defenderCasualties dead-and-injured toll on the defending side, if counted
 */
public record BattleOutcome(
    boolean attackerWins,
    OptionalInt attackerCasualties,
    OptionalInt defenderCasualties
) {

    public BattleOutcome {
        if (attackerCasualties == null || defenderCasualties == null) {
            throw new IllegalArgumentException("casualty counts must be OptionalInt, never null");
        }
        if (attackerCasualties.isPresent() && attackerCasualties.getAsInt() < 0) {
            throw new IllegalArgumentException("attackerCasualties must be non-negative");
        }
        if (defenderCasualties.isPresent() && defenderCasualties.getAsInt() < 0) {
            throw new IllegalArgumentException("defenderCasualties must be non-negative");
        }
    }

    /**
     * An outcome decided without casualty bookkeeping — the winner is
     * known, the bodies were not counted.
     */
    public static BattleOutcome decided(boolean attackerWins) {
        return new BattleOutcome(attackerWins, OptionalInt.empty(), OptionalInt.empty());
    }

    /**
     * An outcome with both sides' tolls counted. Counts must be
     * non-negative; an engagement that fought without losses passes
     * zeros, not empties.
     */
    public static BattleOutcome counted(boolean attackerWins, int attackerCasualties, int defenderCasualties) {
        return new BattleOutcome(attackerWins, OptionalInt.of(attackerCasualties), OptionalInt.of(defenderCasualties));
    }
}
