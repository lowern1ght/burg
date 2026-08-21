package org.lowern1ght.burg.behavior.war;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.people.RaidConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate-cycle test for {@link RaidManager#tick(long, long)}.
 *
 * <p>The value-object side of the contract ({@link RaidConfig} range clamp,
 * {@code cooldownTicks()} arithmetic, the {@code current()} slot lifecycle)
 * is pinned by {@link org.lowern1ght.burg.people.RaidConfigTest}. This
 * class covers the gate itself: the cooldown boundary, the off-by-one guard,
 * the full "fire / wait / fire" cycle the wire site observes, and the live
 * cooldown override {@code BurgConfig#refreshRaidConfig} pushes into
 * {@link RaidConfig#current()} on mod-bus init and on every config reload.
 *
 * <p>Stateless and Minecraft-free — the bare-JVM suite is the cheapest
 * place to pin the wire-format contract end to end.
 */
class RaidManagerTest {

    @AfterEach
    void resetRaidConfig() {
        // The {@code current()} slot is volatile and process-shared; restore
        // the additive default so a {@code setCurrent} in one case does not
        // leak into the next. Same discipline the {@code RaidConfigTest}
        // uses.
        RaidConfig.resetCurrent();
    }

    @Test
    @DisplayName("first raid fires at cooldownTicks(); earlier ticks do not fire")
    void firstFireAtCooldownBoundary() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        assertFalse(RaidManager.tick(0L, 0L),
            "tick(0L, 0L) does not fire — the first raid waits for the cooldown boundary, not tick 0");
        assertFalse(RaidManager.tick(0L, cooldownTicks - 1L),
            "tick one tick before the boundary does not fire — off-by-one guard");
        assertTrue(RaidManager.tick(0L, cooldownTicks),
            "tick fires at gameTime == cooldownTicks() with previousFire=0L");
    }

    @Test
    @DisplayName("without stamping, every tick past the boundary stays fired — the gate is stateless")
    void gateIsStatelessWithoutStamping() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        // The gate does not self-disable: once gameTime crosses the boundary,
        // every subsequent call with the same previousFire returns true. The
        // wire site is the thing that stamps gameTime into previousFire after
        // each fire so the NEXT call sees the cooldown from the new anchor.
        assertTrue(RaidManager.tick(0L, cooldownTicks),
            "first call past the boundary fires");
        assertTrue(RaidManager.tick(0L, cooldownTicks + 1L),
            "subsequent calls with the same previousFire keep firing — the gate does not latch");
        assertTrue(RaidManager.tick(0L, cooldownTicks * 2),
            "and stays fired far past the boundary — stamping is the wire site's job");
    }

    @Test
    @DisplayName("after stamping, the next fire waits cooldownTicks() from the new anchor")
    void stampedAnchorResetsTheCooldown() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        // First fire at the boundary. The wire site would stamp
        // gameTime (= cooldownTicks) as the new previousFire.
        long firstFire = cooldownTicks;
        assertTrue(RaidManager.tick(0L, firstFire),
            "first fire at the cooldown boundary");

        // The cooldown now counts from firstFire, not from 0L.
        assertFalse(RaidManager.tick(firstFire, firstFire + 1L),
            "one tick past the first fire is still in cooldown");
        assertFalse(RaidManager.tick(firstFire, firstFire + cooldownTicks - 1L),
            "cooldownTicks - 1 ticks past the first fire is still in cooldown");

        // The next fire is at firstFire + cooldownTicks().
        long secondFire = firstFire + cooldownTicks;
        assertTrue(RaidManager.tick(firstFire, secondFire),
            "second fire at firstFire + cooldownTicks() — the gate re-anchored on the stamp");
    }

    @Test
    @DisplayName("cooldown override via setCurrent is observed on the next tick call")
    void cooldownOverrideIsLive() {
        // 600s → 12000 ticks is the default; switching to 60s (the floor)
        // shrinks the cooldown so the next fire moves earlier. This is the
        // path BurgConfig.refreshRaidConfig exercises on every config reload.
        RaidConfig.setCurrent(new RaidConfig(60));

        long cooldownTicks = RaidConfig.current().cooldownTicks();
        assertEquals(1200, cooldownTicks, "60s * 20 tps = 1200 ticks (the floor)");

        assertFalse(RaidManager.tick(0L, cooldownTicks - 1L),
            "before the floor's boundary, no fire");
        assertTrue(RaidManager.tick(0L, cooldownTicks),
            "at the floor's boundary, fire — the override is live on the next call");
    }

    @Test
    @DisplayName("earliestNextFire pass-through matches the wire-format arithmetic")
    void earliestNextFirePassThrough() {
        long cooldownTicks = RaidConfig.DEFAULT.cooldownTicks();

        assertEquals(cooldownTicks, RaidManager.earliestNextFire(0L),
            "fresh town's earliestNextFire is cooldownTicks() — additive default for a town that never fired");
        assertEquals(cooldownTicks * 3L, RaidManager.earliestNextFire(cooldownTicks * 2L),
            "after a fire at 2*cooldown, the next earliest is 3*cooldown");
    }
}
