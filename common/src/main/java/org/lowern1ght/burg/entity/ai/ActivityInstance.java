package org.lowern1ght.burg.entity.ai;

import net.minecraft.core.BlockPos;
import org.lowern1ght.burg.town.PlacedBuilding;

/**
 * The runtime, per-NPC state of one activity — what {@link ActivityDef} describes
 * statically, mutated as the NPC walks through the TRAVELING → APPROACHING →
 * PERFORMING phases.
 *
 * <p>{@code ActivityInstance} is the AI's per-tick scratchpad: the {@code def}
 * tells it what kind of activity this is; {@code targetBuilding} and
 * {@code goToPosition} are the resolved target and path; {@code phase} ticks
 * forward as the AI advances. The two APPROACHING-phase fields stay null
 * while {@code phase} is TRAVELING and only get populated when the AI enters
 * APPROACHING for an activity that actually has a target block.
 */
public class ActivityInstance {

    /**
     * The three phases an activity advances through.
     * <ul>
     *   <li>{@link #TRAVELING} — the NPC is en route to the building or its surroundings.</li>
     *   <li>{@link #APPROACHING} — the NPC is in the bounding box and looking for a specific target block.</li>
     *   <li>{@link #PERFORMING} — the NPC is at the target and is doing the work.</li>
     * </ul>
     */
    public enum Phase { TRAVELING, APPROACHING, PERFORMING }

    /** The static activity definition the instance belongs to. */
    public final ActivityDef def;
    /** The building this instance is targeting (null for activities with no building requirement). */
    public final PlacedBuilding targetBuilding;
    /** Current activity phase; mutates forward as the AI advances. */
    public Phase phase;
    /** The path the NPC is following in TRAVELING (or the approach path in APPROACHING); never null after construction. */
    public final GoToPosition goToPosition;

    /** Populated when entering APPROACHING (null for activities without targetBlock). */
    public BlockPos approachTargetPos = null;
    /** Populated when entering APPROACHING (null for activities without targetBlock). */
    public GoToPosition approachGoTo = null;

    /**
     * @param def the static activity definition; never null
     * @param targetBuilding the building the activity targets; nullable for activities without a building requirement
     * @param phase the initial phase (typically TRAVELING)
     * @param goToPosition the initial travel path; never null
     */
    public ActivityInstance(ActivityDef def, PlacedBuilding targetBuilding, Phase phase, GoToPosition goToPosition) {
        this.def = def;
        this.targetBuilding = targetBuilding;
        this.phase = phase;
        this.goToPosition = goToPosition;
    }
}
