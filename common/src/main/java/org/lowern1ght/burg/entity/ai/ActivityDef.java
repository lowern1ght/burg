package org.lowern1ght.burg.entity.ai;

/**
 * The static description of one NPC activity (the {@code "MINE"} / {@code "TILL"}
 * kind, not the {@code "MINING"} / {@code "TILLING"} state — the activity is the
 * definition the AI matches a job-board entry against; the state is what
 * {@link ActivityInstance} carries).
 *
 * @param requiredBuilding the building the activity expects to fire on (e.g. {@code "farm"}); nullable for activities with no building requirement
 * @param heldItem the item the NPC should hold during the activity (e.g. {@code "minecraft:iron_hoe"}); nullable for activities without held items
 * @param animationType the animation the NPC plays while performing the activity; never null
 * @param targetBlock the resource block to seek out (nullable: {@code null} = no APPROACHING phase, e.g. MINE; non-null = scan BB for this block)
 */
public record ActivityDef(
    String requiredBuilding,
    String heldItem,
    AnimationType animationType,
    String targetBlock  // null = no APPROACHING phase (e.g. MINE); non-null = scan BB for this block
) {}
