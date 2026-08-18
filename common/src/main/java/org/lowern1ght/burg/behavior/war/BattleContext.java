package org.lowern1ght.burg.behavior.war;

import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.town.Town;

/**
 * The read-only inputs the {@link BattleStateMachine} needs to make a
 * transition decision for a single squad on a single tick.
 *
 * <p>The context is the value object the driver hands the state machine
 * — it carries the level (in case the machine ever needs to look at
 * the world), the current game tick (for logging/diagnostics), the two
 * towns and their two squads (the cross-reference the state machine
 * might want when reasoning about position relative to the other
 * squad), and the casualty model shared across both squads.
 *
 * <p>Records validate their components in the compact constructor so
 * construction-site bugs are caught at the call site, not three layers
 * down in the state machine.
 */
public record BattleContext(
    ServerLevel level,
    long gameTick,
    Town attacker,
    Town defender,
    Squad attackerSquad,
    Squad defenderSquad,
    CasualtyModel casualties
) {
    public BattleContext {
        if (level == null) throw new IllegalArgumentException("level must be non-null");
        if (attacker == null) throw new IllegalArgumentException("attacker must be non-null");
        if (defender == null) throw new IllegalArgumentException("defender must be non-null");
        if (attackerSquad == null) throw new IllegalArgumentException("attackerSquad must be non-null");
        if (defenderSquad == null) throw new IllegalArgumentException("defenderSquad must be non-null");
        if (casualties == null) throw new IllegalArgumentException("casualties must be non-null");
    }
}
