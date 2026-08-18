package org.lowern1ght.burg.behavior.road;

import net.minecraft.core.BlockPos;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-town bookkeeping of road nodes and the segments the planner has
 * produced for them.
 *
 * <p>Nodes are the {@link BlockPos} endpoints of every planned road —
 * typically the corners of a placed building, plus expansions to the wider
 * world. Segments are the planner's output, ordered by emission time.
 *
 * <p>Read-only views are returned everywhere so the engine cannot accidentally
 * mutate the graph through a getter. The graph itself is mutable; the
 * decision to make it append-only is a future concern.
 */
public final class RoadGraph {

    private final Map<Town, Set<BlockPos>> nodes = new HashMap<>();
    private final Map<Town, List<RoadSegment>> planned = new HashMap<>();

    /** Record a node for a town. The town's set is created on first use. */
    public void addNode(Town town, BlockPos pos) {
        nodes.computeIfAbsent(town, t -> new HashSet<>()).add(pos);
    }

    /** Record a planned segment for a town. The town's list is created on first use. */
    public void planSegment(Town town, RoadSegment segment) {
        planned.computeIfAbsent(town, t -> new ArrayList<>()).add(segment);
    }

    /** Read-only view of the planned segments for a town (empty if none). */
    public List<RoadSegment> plannedFor(Town town) {
        List<RoadSegment> list = planned.get(town);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /** True iff a town has previously registered a node at this position. */
    public boolean hasNode(Town town, BlockPos pos) {
        Set<BlockPos> set = nodes.get(town);
        return set != null && set.contains(pos);
    }

    /** Read-only view of all nodes for a town (empty if none). */
    public Set<BlockPos> nodesFor(Town town) {
        Set<BlockPos> set = nodes.get(town);
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }
}
