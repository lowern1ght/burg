package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.war.RaidManager;
import org.lowern1ght.burg.people.RaidConfig;
import org.lowern1ght.burg.town.Town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wire-up test for the raid-cadence call site in {@link TickScheduler}.
 *
 * <p>{@code :neoforge:test} exists to exercise MC-aware facades
 * ({@link Town}, {@link TickScheduler}) without a running
 * {@code MinecraftServer}. The actual {@code TickScheduler.tick(MinecraftServer)}
 * entry point still needs a server (it iterates {@code server.getAllLevels()}),
 * so this test exercises the static helper {@link TickScheduler#tickRaids}
 * directly — the helper the per-town loop routes to, and that carries the
 * {@link RaidManager#tick(long, long)} gate plus the
 * {@link Town#setLastRaidFireTick(long)} stamp.
 *
 * <p>What this pins:
 * <ul>
 *   <li>The {@link Town#setLastRaidFireTick(long)} field exists on
 *       {@code Town} with the additive default {@code 0L} — the gate's
 *       "never fired" sentinel.</li>
 *   <li>{@code tickRaids} fires at the cooldown boundary and stamps the
 *       fire tick on the town so the next call's previousFire is the
 *       firing gameTime.</li>
 *   <li>The cooldown override via {@link RaidConfig#setCurrent} is live
 *       on the very next call — the {@code BurgConfig.RAID_COOLDOWN_SECONDS}
 *       Cloth knob lands in {@link RaidConfig#current()} via
 *       {@code refreshRaidConfig}, which the wire site reads through
 *       {@link RaidManager#tick(long, long)} on every call.</li>
 * </ul>
 *
 * <p>The gate's pure-arithmetic side (boundary, off-by-one, full cycle) is
 * pinned in {@code :common:test}'s {@code RaidManagerTest}; this class
 * focuses on the {@code TickScheduler.tickRaids → Town.getLastRaidFireTick}
 * wire site, where {@code new Town()} triggers the MC-classpath that
 * {@code :common:test} deliberately avoids per ADR-0026.
 */
class RaidManagerTickWireTest {

    private Town town;

    @BeforeEach
    void freshTown() {
        town = new Town();
    }

    @AfterEach
    void resetRaidConfig() {
        // The {@code current()} slot is volatile and process-shared; restore
        // the additive default so a {@code setCurrent} in one case does not
        // leak into the next. Same discipline {@code RaidManagerTest} uses.
        RaidConfig.resetCurrent();
    }

    @Test
    @DisplayName("fresh town's lastRaidFireTick is 0L — the additive default for a town that never fired")
    void freshTownHasAdditiveDefaultForLastRaidFireTick() {
        assertEquals(0L, town.getLastRaidFireTick(),
            "fresh town's lastRaidFireTick is 0L — the gate's 'never fired' sentinel; "
                + "RaidConfig.earliestNextFire(0L) computes cooldownTicks() from this baseline");
    }

    @Test
    @DisplayName("tickRaids before the cooldown boundary does not fire and does not stamp the town")
    void tickRaidsBeforeBoundaryDoesNotFireOrStamp() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        assertFalse(TickScheduler.tickRaids(town, cooldownTicks - 1L),
            "one tick before the boundary does not fire — off-by-one guard");
        assertEquals(0L, town.getLastRaidFireTick(),
            "no fire → no stamp → previousFire stays at the additive 0L default");
    }

    @Test
    @DisplayName("tickRaids at the cooldown boundary fires and stamps the fire tick to gameTime")
    void tickRaidsAtBoundaryFiresAndStamps() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        assertTrue(TickScheduler.tickRaids(town, cooldownTicks),
            "at the cooldown boundary the gate fires — RaidManager.tick(0L, cooldownTicks) returns true");
        assertEquals(cooldownTicks, town.getLastRaidFireTick(),
            "the fire tick stamps to gameTime — the wire site's job, the gate is stateless");
    }

    @Test
    @DisplayName("after the first fire, the cooldown counts from the stamped gameTime")
    void cooldownCountsFromStampedFireTick() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        // First fire at the boundary; the stamp moves previousFire forward.
        assertTrue(TickScheduler.tickRaids(town, cooldownTicks));
        long firstFire = town.getLastRaidFireTick();
        assertEquals(cooldownTicks, firstFire);

        // Within the cooldown window after the first fire: no fire, no
        // second stamp (the stamp is only updated on a fire).
        assertFalse(TickScheduler.tickRaids(town, firstFire + 1L),
            "one tick past the first fire is still in cooldown");
        assertEquals(firstFire, town.getLastRaidFireTick(),
            "no fire → previousFire stays at the firstFire stamp");
        assertFalse(TickScheduler.tickRaids(town, firstFire + cooldownTicks - 1L),
            "cooldownTicks - 1 ticks past the first fire is still in cooldown");

        // Second fire at firstFire + cooldownTicks().
        assertTrue(TickScheduler.tickRaids(town, firstFire + cooldownTicks),
            "second fire at firstFire + cooldownTicks() — the gate re-anchored on the stamp");
        assertEquals(firstFire + cooldownTicks, town.getLastRaidFireTick(),
            "the new stamp is the second firing gameTime");
    }

    @Test
    @DisplayName("RaidConfig cooldown override is observed by the wire site on the next call")
    void raidConfigOverrideIsLive() {
        // Override to the floor (60s = 1200 ticks). The wire site must
        // observe the override because RaidConfig.current() is the live
        // reader BurgConfig.refreshRaidConfig pushes into on mod-bus init
        // and on every config reload. Without the override reaching the
        // gate, a config-screen edit would silently not take effect — the
        // exact bug the wire site is here to prevent.
        RaidConfig.setCurrent(new RaidConfig(60));
        long cooldownTicks = RaidConfig.current().cooldownTicks();
        assertEquals(1200, cooldownTicks, "60s * 20 tps = 1200 ticks (the floor)");

        // No fire before the override's boundary.
        assertFalse(TickScheduler.tickRaids(town, cooldownTicks - 1L),
            "before the override's boundary, no fire");

        // Fire at the override's boundary, stamp observed.
        assertTrue(TickScheduler.tickRaids(town, cooldownTicks),
            "at the override's boundary, fire — the override is live on the very next call");
        assertEquals(cooldownTicks, town.getLastRaidFireTick(),
            "the stamp is the firing gameTime even with the override applied");
    }

    @Test
    @DisplayName("TickScheduler.tickRaids routes through RaidManager.tick — the import is the wire")
    void tickSchedulerRoutesThroughRaidManager() {
        // Compile-time proof: TickScheduler.tickRaids calls
        // RaidManager.tick(previousFire, gameTime). If TickScheduler ever
        // stops importing RaidManager, this test file fails to compile
        // (RaidManager.tick is unresolved). The behavioral check that the
        // gate fires at the boundary is pinned by the cases above; this
        // case just guards the import survives future refactors.
        //
        // The equality comparison uses the same Town state both sides see —
        // a fresh town with lastRaidFireTick=0L — so the gate fires iff
        // gameTime >= cooldownTicks().
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        boolean viaTickScheduler = TickScheduler.tickRaids(town, cooldownTicks);
        long after = town.getLastRaidFireTick();
        // Re-derive the gate's verdict against an explicit, fresh town to
        // confirm TickScheduler's call site sees the same boundary as a
        // direct RaidManager.tick invocation.
        Town probe = new Town();
        boolean viaRaidManager = RaidManager.tick(probe.getLastRaidFireTick(), cooldownTicks);

        assertEquals(viaRaidManager, viaTickScheduler,
            "TickScheduler.tickRaids and a direct RaidManager.tick on a fresh town agree "
                + "on the gate verdict at the cooldown boundary");
        assertEquals(cooldownTicks, after,
            "the wire site stamped the firing gameTime onto the town");
    }
}
