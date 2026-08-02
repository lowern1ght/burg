package org.dawnoftime.onceuponatown.behavior.road;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.behavior.intent.ExpandIntent;
import org.dawnoftime.onceuponatown.behavior.task.RoadTask;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Front door of the road layer.
 *
 * <p>Takes an {@link ExpandIntent} and produces zero or more {@link RoadTask}s
 * the engine can assign to a builder. The shape is "one task per segment"
 * because that's what the engine's {@link
 * org.dawnoftime.onceuponatown.behavior.BehaviorEngine} per-citizen queue
 * expects — a task is a unit of work for one NPC, not a list of
 * micro-instructions.
 *
 * <p>For the planning slice the builder emits one task per expand intent
 * (single segment). Multi-segment intents (e.g. an ExpandIntent that asks
 * for a wall around the town) are a future phase: the planner would split
 * the path at town boundaries and emit one task per sub-segment.
 */
public final class RoadBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(RoadBuilder.class);

    private final RoadPlanner planner;
    private final RoadLayer layer;
    private final RoadGraph graph;

    public RoadBuilder(RoadPlanner planner, RoadLayer layer) {
        this(planner, layer, new RoadGraph());
    }

    public RoadBuilder(RoadPlanner planner, RoadLayer layer, RoadGraph graph) {
        this.planner = planner;
        this.layer = layer;
        this.graph = graph;
    }

    /** The graph this builder records segments into. */
    public RoadGraph graph() {
        return graph;
    }

    /**
     * Plan the route for an expand intent and produce one {@link RoadTask} per
     * segment. Side effect: records the intent's endpoints as nodes and the
     * resulting segment in the {@link RoadGraph}.
     */
    public List<RoadTask> planTasks(ExpandIntent intent, Town town, ServerLevel level) {
        if (intent == null || town == null || level == null) {
            LOGGER.debug("[ROAD] planTasks called with null argument (intent={}, town={}, level={})"
                + " -- returning empty task list", intent, town, level);
            return List.of();
        }

        // 1) Record the endpoints as nodes (idempotent — HashSet).
        graph.addNode(town, intent.from());
        graph.addNode(town, intent.to());

        // 2) Ask the planner for a route.
        RoadSegment segment = planner.plan(intent.from(), intent.to(), level);
        graph.planSegment(town, segment);

        // 3) Resolve the piece id and emit one task.
        ResourceLocation pieceNbt = layer.pieceFor(segment);
        List<RoadTask> tasks = new ArrayList<>();
        tasks.add(new RoadTask(segment, pieceNbt));
        LOGGER.debug("[ROAD] planned segment from={} to={} type={} waypoints={} pieceNbt={}",
            intent.from(), intent.to(), segment.type(), segment.length(), pieceNbt);
        return tasks;
    }
}
