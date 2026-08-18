package org.lowern1ght.burg.people;

/**
 * Why somebody is no longer in the town.
 *
 * <p>Recorded rather than inferred, because these are not interchangeable to a player. A town
 * that buried its old is not a town that starved them, and neither is a town people walked out
 * of — and the third one is the player's fault in a way the first is not.
 */
public enum Departure {

    /** Old age. The one a settlement is supposed to have. */
    AGE,

    /** Went without food for too many days running. */
    STARVED,

    /**
     * Walked out.
     *
     * <p>The self-limiting control on overcrowding, and the honest one. Homelessness is allowed
     * — a town may outgrow its beds, and the crowding is felt — but a person who has been
     * miserable long enough leaves rather than staying miserable forever. So misery cannot
     * accumulate without bound, and the player sees a legible failure ("people are leaving")
     * with an obvious fix, instead of a silent cap that refuses births for no visible reason.
     */
    LEFT,

    /** Died to something in the world. */
    KILLED,

    /** From an older save, or from a caller that did not say. */
    UNRECORDED
}
