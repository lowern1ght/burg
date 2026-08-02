package org.dawnoftime.onceuponatown.behavior.war;

/**
 * The purpose of a squad in a battle.
 *
 * <p>Carried by {@link Squad} so the battle state machine and any future
 * behaviour-under-combat code can branch on intent without inspecting
 * direction. {@link #ATTACK} squads march on the defender's anchor;
 * {@link #DEFEND} squads hold at their own anchor and try to repulse
 * attackers; {@link #RETREAT} is the state a squad moves into once
 * {@link BattleState#RETREATING} is the active state — it is not a separate
 * goal but a transition, and the {@link SquadGoal} lets a caller ask
 * "where is this squad trying to go" without reading the state machine.
 */
public enum SquadGoal {
    ATTACK,
    DEFEND,
    RETREAT
}
