package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.war.RaidManager;
import org.lowern1ght.burg.people.RaidConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the raid-cadence call site,
 * {@link RaidManager}. {@link org.lowern1ght.burg.tick.TickScheduler#tick}
 * itself requires a {@code MinecraftServer} + per-town world state — that
 * path lands in the future {@code gametest} source set, not here. The
 * static {@link RaidManager#tick(long, long)} helper the
 * {@code TickScheduler} routes to (act-5 wire-up) is Minecraft-free at
 * the call site and exercises the raid-config cooldown contract
 * directly, which is the piece worth pinning in this carve.
 *
 * <p>The cooldown lives in {@link RaidConfig#current()}, a volatile slot
 * the infrastructure pushes into on mod-bus init and on every config
 * reload. The test resets to {@link RaidConfig#DEFAULT} in
 * {@link #resetRaidConfig()} so a {@code setCurrent} in one case does
 * not leak into the next.
 *
 * <p>Wire-format contract pinned here (per
 * {@code openspec/changes/hub-becomes-window/specs/construction-mode-supply-mode
 * §"Requirement: structural predicate is three conditions AND-ed"}):
 * the next raid's earliest-fire time is
 * {@code previousFire + current().cooldownTicks()}.
 * {@link RaidManager#tick(long, long)} is the gate;
 * {@link RaidManager#earliestNextFire(long)} is the convenience pass-through.
 */
class TickSchedulerRaidTest {

    @AfterEach
    void resetRaidConfig() {
        // Restore the additive default so the next test starts from a
        // known baseline. Same discipline the bare-JVM tests use for
        // their volatile slots.
        RaidConfig.resetCurrent();
    }

    @Test
    @DisplayName("RaidManager.tick(previousFire=0L, gameTime=cooldownTicks()) fires on the cooldown boundary")
    void tickFiresAtCooldownBoundary() {
        // previousFire=0L is the additive default for a town whose first
        // raid has not yet fired. earliestNextFire(0L) = cooldownTicks()
        // by definition, so the gate fires on the very first tick at the
        // boundary. This pins the wire contract without needing a server.
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        assertTrue(RaidManager.tick(0L, cooldownTicks),
            "tick fires at gameTime == cooldownTicks() with previousFire=0L — "
                + "the cooldown counts from town registration");
        assertEquals(cooldownTicks, RaidManager.earliestNextFire(0L),
            "earliestNextFire(0L) is exactly cooldownTicks() — the convention the wire site reads");
        assertFalse(RaidManager.tick(0L, cooldownTicks - 1L),
            "tick does NOT fire one tick before the boundary — off-by-one guard");
    }
}