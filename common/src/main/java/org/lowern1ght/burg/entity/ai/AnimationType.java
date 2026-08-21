package org.lowern1ght.burg.entity.ai;

/**
 * The animation the NPC plays while performing an {@link ActivityDef}.
 * Three values cover the current corpus; a config that wants finer-grained
 * activity categories reuses one of these for the new entry, since the
 * renderer only knows the three.
 */
public enum AnimationType {
    /** Swings a tool at a block. */
    MINE,
    /** Stands at a bench, hands busy, no swing. */
    CRAFT,
    /**
     * Swings an axe at timber.
     *
     * <p>Mechanically MINE's animation, and named separately anyway: a config that says CHOP at
     * the lumberjack's log stack reads as what it is, and a job list is read far more often than
     * it is written.
     */
    CHOP
}
