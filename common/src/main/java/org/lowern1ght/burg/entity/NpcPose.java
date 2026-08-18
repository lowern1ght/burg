package org.lowern1ght.burg.entity;

/**
 * What a person's body is doing, as one value.
 *
 * <p><b>An enum and not a flag per pose, on purpose.</b> The first pose this mod had — reading a
 * plan — was a synced boolean, and the second one that was attempted was another boolean, and by
 * the third the model would have been asking four independent questions that can contradict each
 * other: reading while sitting while talking. One byte on the wire, one answer, and adding the
 * next animation costs an enum constant and a few rotations.
 *
 * <p>The server decides, always. A pose is a fact about what somebody is doing, and the client
 * cannot know that a person has no bed tonight or is stood next to a neighbour.
 */
public enum NpcPose {

    /** Walking, standing, working — whatever {@code super.setupAnim} does on its own. */
    STANDING,

    /** Both hands on a plan, head tilted down. The builder's. */
    READING,

    /**
     * Sitting on the ground.
     *
     * <p>The rotations are <b>vanilla's own</b>, read out of {@code HumanoidModel.setupAnim}'s
     * riding branch by decompiling it rather than by eye. That matters here specifically: the last
     * pose this mod borrowed came from {@code VillagerModel}, where crossed arms are a single
     * pre-modelled cube, and applying its numbers to two separate hanging arms raised them
     * straight out in front like a zombie. Riding is a HUMANOID pose on the same skeleton, so the
     * numbers transfer exactly.
     */
    SITTING,

    /**
     * Sitting with the head down: asleep where they sat.
     *
     * <p>For somebody with no bed. A person left standing in the street all night reads as a
     * broken schedule; a person dozing on the ground reads as a town that has not built enough
     * houses, which is exactly what it is.
     */
    DOZING,

    /**
     * Facing a neighbour, one hand up.
     *
     * <p>The cheapest liveliness there is: two idle people who happen to be near each other turn
     * and talk. This one is <b>invented rather than borrowed</b> — vanilla has no gesture on a
     * humanoid to copy — so it is the one pose in this list whose numbers want a look in game
     * before they are trusted.
     */
    TALKING;

    /** For the wire and for NBT, so neither carries an enum name a rename would break. */
    public int index() { return ordinal(); }

    public static NpcPose byIndex(int index) {
        NpcPose[] all = values();
        return all[Math.min(all.length - 1, Math.max(0, index))];
    }

    public boolean isSeated() { return this == SITTING || this == DOZING; }
}
