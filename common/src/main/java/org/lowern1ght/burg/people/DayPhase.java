package org.lowern1ght.burg.people;

/**
 * What part of the day it is, from Minecraft's own clock.
 *
 * <p>In this package and therefore with no Minecraft import in it, which is the point: the phase
 * boundaries are a rule about how a settlement lives, and a rule belongs where it can be tested.
 * A caller passes {@code level.getDayTime()}; nothing here knows what a level is.
 *
 * <p>The boundaries are vanilla's own, so a citizen's day lines up with everything a player
 * already reads from the sky rather than with numbers of ours:
 *
 * <ul>
 *   <li>{@code 0} — sunrise, where vanilla's own day starts</li>
 *   <li>{@code 11500} — the sun begins to set</li>
 *   <li>{@code 13000} — dark enough for mobs</li>
 *   <li>{@code 23000} — first light</li>
 * </ul>
 *
 * <p>{@link #DUSK} exists as its own phase rather than being folded into night because that is
 * when the interesting behaviour is: a person walking home while the light goes is the thing a
 * player actually watches, and it needs a window of its own to happen in.
 */
public enum DayPhase {

    /** Work, trade, be out of doors. */
    DAY,

    /** Stop work and walk home. About a minute and a half of real time. */
    DUSK,

    /** In bed, or at least indoors. */
    NIGHT,

    /** Out of bed, not yet at work. */
    DAWN;

    public static final long DAY_LENGTH = 24000L;
    public static final long DUSK_AT = 11500L;
    public static final long NIGHT_AT = 13000L;
    public static final long DAWN_AT = 23000L;

    /**
     * @param dayTime {@code level.getDayTime()}; any value, including many days' worth
     */
    public static DayPhase of(long dayTime) {
        long t = Math.floorMod(dayTime, DAY_LENGTH);
        if (t < DUSK_AT) return DAY;
        if (t < NIGHT_AT) return DUSK;
        if (t < DAWN_AT) return NIGHT;
        return DAWN;
    }

    /** Whether a person should be at work. */
    public boolean isWorkingTime() { return this == DAY; }

    /** Whether a person should be heading for or lying in a bed. */
    public boolean isRestingTime() { return this == DUSK || this == NIGHT; }

    /** Whether a person should actually be asleep, as opposed to walking home. */
    public boolean isSleepingTime() { return this == NIGHT; }
}
