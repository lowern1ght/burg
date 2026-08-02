package org.dawnoftime.onceuponatown.behavior.task;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.behavior.intent.TownIntent;
import org.dawnoftime.onceuponatown.behavior.path.RoadSegment;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.UUID;

/**
 * A {@link CitizenTask} that represents the planning of a road segment.
 *
 * <p><b>Scope (this revision):</b> planning only. The task holds the
 * {@link RoadSegment} the planner produced and the {@link ResourceLocation}
 * of the NBT piece the {@link
 * org.dawnoftime.onceuponatown.behavior.path.RoadLayer} resolved for it.
 * On the first tick the task transitions {@link TaskState#PENDING} →
 * {@link TaskState#IN_PROGRESS}; on the second tick it transitions to
 * {@link TaskState#DONE}. No blocks are placed.
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
public final class PathTask implements CitizenTask {

    private final UUID id;
    private final TownIntent source;
    private final Npc assignee;
    private final RoadSegment segment;
    private final ResourceLocation pieceNbt;
    private TaskState state;

    /**
     * Production constructor. The planner-built segment + the layer-resolved
     * piece NBT are the inputs; the task carries both through to the future
     * placement commit.
     */
    public PathTask(RoadSegment segment, ResourceLocation pieceNbt) {
        this.id = UUID.randomUUID();
        this.source = null;
        this.assignee = null;
        this.segment = segment;
        this.pieceNbt = pieceNbt;
        this.state = TaskState.PENDING;
    }

    /**
     * Legacy stub constructor. Used by the existing queue tests and any
     * other code that needs a {@link PathTask} without going through the
     * planner. The task {@link TaskState#FAILED} on first tick — the same
     * no-op behaviour the pre-revision stub had.
     */
    public PathTask(UUID id, TownIntent source, Npc assignee) {
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
    @Override public boolean isInterruptible() { return true; }

    /** The planned road segment, or null for the legacy stub form. */
    public RoadSegment segment() { return segment; }

    /** The NBT piece to place, or null for the legacy stub form. */
    public ResourceLocation pieceNbt() { return pieceNbt; }

    @Override
    public TaskState tick(TaskContext ctx) {
        if (state.isTerminal()) return state;

        // Legacy stub form: behave like the pre-revision stub (PENDING -> FAILED).
        if (segment == null) {
            state = TaskState.FAILED;
            return state;
        }

        // Planning-only slice: PENDING -> IN_PROGRESS -> DONE across two ticks.
        if (state == TaskState.PENDING) {
            state = TaskState.IN_PROGRESS;
            return state;
        }
        if (state == TaskState.IN_PROGRESS) {
            state = TaskState.DONE;
            return state;
        }
        return state;
    }
}
