package org.lowern1ght.burg.behavior.task;

import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.behavior.intent.TownIntent;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.entity.Npc;

import java.util.UUID;

/**
 * A {@link CitizenTask} that represents the planning of a road segment.
 *
 * <p><b>Scope (this revision):</b> planning only. The task holds the
 * {@link RoadSegment} the planner produced and the {@link ResourceLocation}
 * of the NBT piece the {@link
 * org.lowern1ght.burg.behavior.road.RoadLayer} resolved for it.
 * Each tick the task advances a morale-weighted progress counter and exposes
 * the matching {@link #currentWaypoint()}. No blocks are placed.
 *
 * <p><b>Phase 7 morale:</b> progress grows by {@link #BASE_RATE} per tick,
 * scaled by the assignee's morale multiplier (default 1.0 when no assignee is
 * recorded). {@link #currentWaypoint()} is the linear projection of progress
 * onto the segment's waypoint count. At neutral morale (50), ten ticks reach
 * DONE.
 *
 * <p>Actual NBT placement will be wired in a follow-up commit once the
 * streets NBTs are validated. The interface stays exactly the same —
 * placement will be added inside {@link #tick} without touching the
 * constructor or the public surface.
 *
 * <p>The legacy {@code (UUID, TownIntent, Npc)} constructor is preserved
 * for the existing queue tests that use the empty / no-citizen form. It
 * yields a stub tick that {@link TaskState#FAILED} on first tick, matching
 * the pre-revision stub behaviour. The new planner-driven constructor is
 * the production path.
 */
public final class RoadTask implements CitizenTask {

    /** Per-tick increment at morale=50 (multiplier 1.0). 10 ticks = full completion. */
    private static final float BASE_RATE = 0.1f;

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final RoadSegment segment;
    private final ResourceLocation pieceNbt;
    private TaskState state;
    private float progress = 0f;
    private int currentWaypoint = 0;

    /**
     * Production constructor. The planner-built segment + the layer-resolved
     * piece NBT are the inputs; the task carries both through to the future
     * placement commit. {@code assignee} may be null for the legacy planner-
     * only path -- {@link #moraleMultiplier} treats null as 1.0x.
     */
    public RoadTask(RoadSegment segment, ResourceLocation pieceNbt) {
        this.id = UUID.randomUUID();
        this.source = null;
        this.assignee = null;
        this.segment = segment;
        this.pieceNbt = pieceNbt;
        this.state = TaskState.PENDING;
    }

    /**
     * Legacy stub constructor. Used by the existing queue tests and any
     * other code that needs a {@link RoadTask} without going through the
     * planner. The task {@link TaskState#FAILED} on first tick -- the same
     * no-op behaviour the pre-revision stub had.
     */
    public RoadTask(UUID id, TownIntent source, Npc assignee) {
        this.id = id;
        this.source = source;
        this.assignee = assignee;
        this.segment = null;
        this.pieceNbt = null;
        this.state = TaskState.PENDING;
    }

    @Override public UUID id() { return id; }
    @Override public TownIntent source() { return source; }
    @Override public Npc assignee() { return assignee; }
    @Override public TaskState state() { return state; }
    @Override public float progress() { return progress; }
    @Override public boolean isInterruptible() { return true; }

    /** The planned road segment, or null for the legacy stub form. */
    public RoadSegment segment() { return segment; }

    /** The NBT piece to place, or null for the legacy stub form. */
    public ResourceLocation pieceNbt() { return pieceNbt; }

    /**
     * The waypoint the task has currently reached, as a 0-based index into
     * {@code segment.waypoints()}. Tracks {@link #progress()} linearly so the
     * caller can render a road under construction without doing the math
     * themselves. Always 0 for the legacy stub form (no segment).
     */
    public int currentWaypoint() { return currentWaypoint; }

    @Override
    public TaskState tick(TaskContext ctx) {
        if (state.isTerminal()) return state;

        // Legacy stub form: behave like the pre-revision stub (PENDING -> FAILED).
        if (segment == null) {
            state = TaskState.FAILED;
            return state;
        }

        // PENDING -> STARTED: the planning slice is now under way.
        if (state == TaskState.PENDING) {
            state = TaskState.STARTED;
        }

        // STARTED -> IN_PROGRESS: subsequent ticks advance progress.
        if (state == TaskState.STARTED) {
            state = TaskState.IN_PROGRESS;
        }

        // Morale-weighted progress; null assignee or null morale returns 1.0 from
        // the default moraleMultiplier, so the production constructor (no assignee)
        // progresses at the base rate.
        float mult = moraleMultiplier(ctx.morale(), assignee);
        progress = Math.min(1.0f, progress + BASE_RATE * mult);

        // Project progress onto the waypoint list so callers can render under-
        // construction state without re-doing the math.
        int totalWaypoints = segment.waypoints().size();
        currentWaypoint = Math.min(totalWaypoints, (int) (progress * totalWaypoints));

        if (progress >= 1.0f) {
            state = TaskState.DONE;
        }
        return state;
    }
}
