package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.executor.BuildExecutor;
import org.dawnoftime.onceuponatown.behavior.intent.NpcSupplier;
import org.dawnoftime.onceuponatown.behavior.morale.MoraleState;

/**
 * What a {@link CitizenTask} sees when its {@code tick} method is called.
 *
 * <p>Passes the world, the current tick, the engine's NPC lookup, the engine's
 * {@link BuildExecutor} seam (so a task can query placement / queue new builds
 * without holding the seam as its own field), and the engine's {@link MoraleState}
 * (so a task can compute its progress multiplier). The level is the live
 * server-level, not a client side mirror.
 *
 * <p>Records are immutable so a task can't accidentally mutate the context — the kind of
 * thing that would make a "why does the engine see a tick 0 sometimes?" bug extremely hard
 * to find.
 */
public record TaskContext(
        ServerLevel level,
        long gameTick,
        NpcSupplier npcSupplier,
        BuildExecutor executor,
        MoraleState morale
) {
}
