package org.dawnoftime.onceuponatown.behavior.path;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * A planned road: a sequence of waypoints from {@code start} to {@code end},
 * classified by what kind of piece it is.
 *
 * <p>Planning-only — this record is the planner's output, not a placement
 * command. A future phase will hand the {@link #waypoints()} to a placer
 * that walks the sequence and drops the resolved NBT piece at each step.
 * For now the segment is recorded and the engine moves on.
 *
 * <p>{@link #waypoints()} is defensively copied via {@link List#copyOf} so
 * downstream consumers cannot mutate the planner's internal lists or
 * accidentally share a mutable list across segments.
 */
public record RoadSegment(
        BlockPos start,
        BlockPos end,
        List<BlockPos> waypoints,
        RoadType type
) {
    public RoadSegment {
        waypoints = List.copyOf(waypoints);
    }

    /** Number of waypoints, including start and end. */
    public int length() {
        return waypoints.size();
    }
}
