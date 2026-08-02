package org.dawnoftime.onceuponatown.behavior.war;

import net.minecraft.core.BlockPos;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.List;
import java.util.UUID;

/**
 * An aggregate combat unit. A squad is one row in the war ledger, not one
 * individual NPC — the combat is resolved at squad scale (per VISION.md
 * "scale problem": war-scale combat is NPC-vs-NPC at 60+ bodies, not
 * vanilla 1v1).
 *
 * <p>The squad carries:
 * <ul>
 *   <li>An immutable id used by {@link org.dawnoftime.onceuponatown.behavior.BattleDriver}
 *       to key per-squad state.</li>
 *   <li>A display name (debug aid).</li>
 *   <li>The roster of NPCs (non-null; an empty roster is meaningful for
 *       the first-slice stub squad returned by the battle driver before
 *       real squad selection lands).</li>
 *   <li>A {@link SquadGoal} — what the squad is trying to do.</li>
 *   <li>A target position — where the squad is heading. For an ATTACK
 *       squad this is the defender's anchor; for a DEFEND squad it is
 *       the town's own anchor.</li>
 * </ul>
 *
 * <p>{@link #averagePosition()} and {@link #distanceToTarget()} are the
 * aggregate geometry the battle state machine reads. They make the squad
 * behave as a single point even when the underlying members are spread.
 * An empty roster reports its target position as the average and a
 * zero distance to it — the state machine treats that as a routed squad
 * via {@link CasualtyModel#averageHealth}.
 */
public record Squad(
    UUID id,
    String name,
    List<Npc> members,
    SquadGoal goal,
    BlockPos targetPosition
) {
    public Squad {
        if (id == null) throw new IllegalArgumentException("id must be non-null");
        if (members == null) {
            throw new IllegalArgumentException("members must be non-null");
        }
        if (name == null) throw new IllegalArgumentException("name must be non-null");
        if (goal == null) throw new IllegalArgumentException("goal must be non-null");
        if (targetPosition == null) throw new IllegalArgumentException("targetPosition must be non-null");
        members = List.copyOf(members);
    }

    /**
     * The geometric centre of the squad: the average of every member's
     * block position. The battle state machine uses this to compute
     * distance to the target rather than asking any member's position
     * (which would bias the result if rosters are uneven). An empty
     * roster (first-slice stub) returns the target position itself — the
     * squad is "at" the target it has nobody to walk to.
     */
    public BlockPos averagePosition() {
        if (members.isEmpty()) return targetPosition;
        int x = 0;
        int y = 0;
        int z = 0;
        for (Npc n : members) {
            BlockPos pos = n.blockPosition();
            x += pos.getX();
            y += pos.getY();
            z += pos.getZ();
        }
        int size = members.size();
        return new BlockPos(x / size, y / size, z / size);
    }

    /**
     * The distance from the squad's centre to its target position. Returns
     * {@code distSqr}'s square root cast to {@code double} so callers
     * don't have to remember the conversion. An empty roster at its
     * target returns 0.
     */
    public double distanceToTarget() {
        return Math.sqrt(averagePosition().distSqr(targetPosition));
    }
}
