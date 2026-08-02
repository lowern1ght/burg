package org.dawnoftime.onceuponatown.behavior.diplomacy;

/**
 * The high-level posture between two towns.
 *
 * <p>{@link #NEUTRAL} is the default — towns that have never interacted read
 * as NEUTRAL via {@link DiplomaticRegistry#between}. The other three are
 * deliberately chosen states, set by {@link DiplomaticAction} records flowing
 * through the registry.
 *
 * <p>Combat details (which soldiers march, what damage each side does, who
 * surrenders) live in the Act 5 war model and are deliberately not on this
 * enum. {@code AT_WAR} here just means "these two towns' production goes
 * into armies, not trade".
 */
public enum DiplomaticStatus {
    NEUTRAL,
    ALLY,
    TRUCE,
    AT_WAR
}
