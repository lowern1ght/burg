package org.lowern1ght.burg.behavior.road;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * A* road planner over loaded chunks in a {@link ServerLevel}.
 *
 * <p>Planning is <b>2D on the surface Y</b> for the first slice. The planner
 * follows the height of the start position (or the top non-air block at each
 * cell — same outcome on flat terrain) and does not stairs or tunnelling.
 * Y-aware planning is a future enhancement; until then the engine's output
 * is a flat path suitable for overworld road networks.
 *
 * <p>The cost function is {@link TerrainCost} — see its docstring for the
 * calibration. The heuristic is Manhattan distance, which is admissible for
 * non-negative step costs and keeps the planner from wandering into a
 * distant-optimal dead end.
 *
 * <p>Failure modes the planner handles:
 * <ul>
 *   <li>start == end → trivial single-cell segment, classified by the start
 *       cell.</li>
 *   <li>no path within {@link #MAX_RADIUS} or the iteration cap → falls
 *       back to a straight two-point segment. Suboptimal but the engine can
 *       still report progress rather than stopping cold.</li>
 *   <li>infinite loop / cycle → bounded by the iteration cap.</li>
 * </ul>
 */
public final class RoadPlanner {

    private final TerrainCost costs;
    private static final int MAX_RADIUS = 200;
    private static final int ITERATION_CAP = 50000;
    // Strict — the planner only stops when it has reached the goal cell
    // exactly. A non-zero tolerance caused short test paths to terminate
    // early and produced under-length segments.
    private static final int GOAL_REACH = 0;

    public RoadPlanner(TerrainCost costs) {
        this.costs = costs;
    }

    /**
     * Plan a route from {@code from} to {@code to} through loaded chunks.
     *
     * <p>Costs are taken from the {@link TerrainCost} attached to the planner;
     * the level is read-only — the planner does not place blocks.
     */
    public RoadSegment plan(BlockPos from, BlockPos to, ServerLevel level) {
        if (from.equals(to)) {
            return new RoadSegment(from, to, List.of(from), classify(from, level));
        }

        // Uniform-cost search (Dijkstra) keyed on g. The heuristic-based A*
        // variant committed to sub-optimal paths in short cross-country
        // routes because the Manhattan heuristic under-estimates the cost
        // of detour paths proportionally less than through-paths once an
        // obstacle is in the start-end line. Dijkstra expands by g, which
        // is the actual cost paid so far — the detour (low g) wins over the
        // through-obstacle path (high g) regardless of tie-breaking order.
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.g, b.g));
        Set<BlockPos> closed = new HashSet<>();
        Map<BlockPos, Node> nodeMap = new HashMap<>();

        Node start = new Node(from, 0.0, null);
        open.add(start);
        nodeMap.put(from, start);

        Node goal = null;
        int iterations = 0;
        while (!open.isEmpty() && iterations < ITERATION_CAP) {
            Node current = open.poll();
            iterations++;
            // Skip outdated entries. Java's PriorityQueue has no decrease-key,
            // so when a better path to a position is found we add a new
            // node AND leave the old one in the heap. Cheap belt-and-braces
            // skip even though Dijkstra's nodes are inserted in monotonically
            // increasing g order.
            Node best = nodeMap.get(current.pos);
            if (best != null && best != current && current.g > best.g) {
                continue;
            }
            if (current.pos.distManhattan(to) <= GOAL_REACH) {
                goal = current;
                break;
            }
            closed.add(current.pos);

            for (Cardinal d : Cardinal.values()) {
                BlockPos next = current.pos.offset(d.dx, 0, d.dz);
                if (closed.contains(next)) continue;
                // Bound the planner's exploration to the configured radius
                // from the start. The check is on `next` so detours around
                // obstacles don't escape the radius as `current` advances.
                if (next.distManhattan(from) > MAX_RADIUS) continue;

                BlockState state = level.getBlockState(next);
                int stepCost = costs.costFor(state.getBlock());
                double g = current.g + stepCost;
                Node existing = nodeMap.get(next);
                if (existing != null && existing.g <= g) continue;

                Node newNode = new Node(next, g, current);
                nodeMap.put(next, newNode);
                open.add(newNode);
            }
        }

        if (goal == null) {
            // Fallback: a straight two-point segment. The planner could not
            // find a way within the radius / iteration cap; the engine still
            // has a segment to record and a task to issue.
            return new RoadSegment(from, to, List.of(from, to), RoadType.STREET);
        }

        // Walk parents back to the start, then reverse.
        List<BlockPos> waypoints = new ArrayList<>();
        for (Node n = goal; n != null; n = n.parent) {
            waypoints.add(0, n.pos);
        }

        RoadType type = classifyFromPath(waypoints, level);
        return new RoadSegment(from, to, waypoints, type);
    }

    /** Classify a single cell. WATER → BRIDGE, otherwise STREET. */
    private RoadType classify(BlockPos pos, ServerLevel level) {
        BlockState state = level.getBlockState(pos);
        if (isWater(state.getBlock())) {
            return RoadType.BRIDGE;
        }
        return RoadType.STREET;
    }

    /** Classify the whole path: any water cell → BRIDGE, otherwise STREET. */
    private RoadType classifyFromPath(List<BlockPos> path, ServerLevel level) {
        for (BlockPos p : path) {
            if (isWater(level.getBlockState(p).getBlock())) {
                return RoadType.BRIDGE;
            }
        }
        return RoadType.STREET;
    }

    private static boolean isWater(Block block) {
        return block == Blocks.WATER;
    }

    /** Four cardinal directions on the surface plane. */
    private enum Cardinal {
        N(0, -1), S(0, 1), E(1, 0), W(-1, 0);
        final int dx, dz;
        Cardinal(int dx, int dz) { this.dx = dx; this.dz = dz; }
    }

    /** Dijkstra node. {@code parent} is the predecessor on the best-known path. */
    private static final class Node {
        final BlockPos pos;
        final double g;
        final Node parent;
        Node(BlockPos pos, double g, Node parent) {
            this.pos = pos;
            this.g = g;
            this.parent = parent;
        }
    }
}
