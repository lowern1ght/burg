package org.lowern1ght.burg.entity.ai;

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
