package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.NpcSupplier;

/**
 * What a {@link CitizenTask} sees when its {@code tick} method is called.
 *
 * <p>Passes the world, the current tick, and the engine's NPC lookup so a task can resolve
 * nearby citizens without reaching into globals. The level is the live server-level, not a
 * client side mirror.
 *
 * <p>Records are immutable so a task can't accidentally mutate the context — the kind of
 * thing that would make a "why does the engine see a tick 0 sometimes?" bug extremely hard
 * to find.
 */
public record TaskContext(
        ServerLevel level,
        long gameTick,
        NpcSupplier npcSupplier
) {
}
