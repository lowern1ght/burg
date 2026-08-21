package org.lowern1ght.burg.behavior.war;

import org.lowern1ght.burg.infrastructure.config.BurgConfig;
import org.lowern1ght.burg.people.RaidConfig;

/**
 * Stub for the raid-cadence call site — small enough to test in bare-JVM
 * but with a real wire path: the {@link #tick(long, long)} method uses
 * {@link RaidConfig#current()} for the next-fire guard so the
 * {@link BurgConfig#RAID_COOLDOWN_SECONDS} Cloth knob has a live reader.
 *
 * <p><b>Wire-format semantics</b> ({@code hub-becomes-window §"Requirement:
 * structural predicate is three conditions AND-ed"}): the next raid's
 * earliest-fire time is {@code previousFire + current().cooldownTicks()}.
 * {@link RaidConfig#earliestNextFire(long)} centralises that arithmetic;
 * {@link #tick(long, long)} is the call site the {@code TickScheduler}
 * will route to once the act-5 wiring lands.
 *
 * <p><b>Future call site</b> (act-5 follow-up, documented here so the
 * reader knows where {@link RaidConfig#current()} will be hit per tick):
 * <pre>{@code
 *   // TickScheduler.tickQuests → TickScheduler.tickRaids (act-5 wire-up)
 *   if (RaidManager.tick(town.getLastRaidFireTick(), level.getGameTime())) {
 *       // schedule a new raid; the next call's previousFire becomes the
 *       // gameTime at which this branch fired.
 *   }
 * }</pre>
 *
 * <p>Until that wiring lands, this class is a value-object facade: no
 * state, no Minecraft imports, the only thing that touches the
 * configuration is the {@link RaidConfig#current()} slot the
 * {@link org.lowern1ght.burg.infrastructure.config.BurgConfig#refreshRaidConfig()}
 * push fills.
 */
public final class RaidManager {

    private RaidManager() {}

    /**
     * The next-fire guard. Returns {@code true} iff the configured cooldown
     * has elapsed since {@code previousFire} at the {@code gameTime} tick —
     * i.e. {@code gameTime >= RaidConfig.current().earliestNextFire(previousFire)}.
     *
     * <p>A {@code previousFire} of {@code 0L} (the additive default for a
     * town whose first raid has not yet fired) yields a next-fire of
     * {@code cooldownTicks()} on the very first tick — i.e. the cooldown
     * counts from town registration, not from world load.
     *
     * <p>Stateless, side-effect-free, no Minecraft import. The {@code act-5}
     * carve that wires {@code TickScheduler.tickRaids} will call this per
     * tick per town; the bare-JVM test exercises the contract directly.
     */
    public static boolean tick(long previousFire, long gameTime) {
        return gameTime >= RaidConfig.current().earliestNextFire(previousFire);
    }

    /**
     * The earliest tick the next raid may fire, given the tick of the
     * previous raid. Convenience pass-through for call sites that want
     * the long directly rather than the boolean gate.
     */
    public static long earliestNextFire(long previousFire) {
        return RaidConfig.current().earliestNextFire(previousFire);
    }
}
