package org.lowern1ght.burg.behavior.war;

/**
 * The five states a squad can be in during a battle.
 *
 * <p>Transitions are deterministic and run from {@link BattleStateMachine}:
 * <ul>
 *   <li>{@link #ADVANCING} — moving toward the target position.</li>
 *   <li>{@link #ENGAGING} — within engage range, in combat.</li>
 *   <li>{@link #RETREATING} — health has fallen below the retreat threshold
 *       but the squad is still organised.</li>
 *   <li>{@link #ROUTED} — health has fallen below the rout threshold; the
 *       squad is broken and individual NPCs flee. Terminal.</li>
 *   <li>{@link #VICTORIOUS} — the squad has reached its target and its
 *       health is still acceptable. Terminal.</li>
 * </ul>
 *
 * <p>Routing and victory are terminal: the squad never recovers. Reinforcement
 * of a retreated squad is a separate concern handled outside the state
 * machine.
 */
public enum BattleState {
    ADVANCING,
    ENGAGING,
    RETREATING,
    ROUTED,
    VICTORIOUS
}
