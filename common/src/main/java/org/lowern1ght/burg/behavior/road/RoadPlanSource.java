package org.lowern1ght.burg.behavior.road;

import org.lowern1ght.burg.town.Town;

import java.util.List;

/**
 * Tick-driven source of {@link RoadSegment}s the {@code TickScheduler.tickRoadPlans}
 * seam wires into {@link Town#addRoadSegment(RoadSegment)}.
 *
 * <p>The seam keeps two contracts in one shape:
 * <ol>
 *   <li><b>Production-callable.</b> The {@code TickScheduler.tick(MinecraftServer)}
 *       per-town loop calls {@link TickScheduler#tickRoadPlans(Town, long)},
 *       which delegates to the installed source's {@link #planFor(Town, long)}.
 *       The source is the seam the production caller (the act-4 transition
 *       owner) plugs its planning path into.</li>
 *   <li><b>Test-injectable.</b> Bare-JVM and {@code :neoforge:test} targets
 *       install a fake source via
 *       {@link TickScheduler#setRoadPlanSource(RoadPlanSource)} to drive the
 *       helper's {@code try/catch + addRoadSegment + boolean-return} shape
 *       without an MC server. The {@link #NONE} constant preserves the
 *       pre-carve default (no source → empty list → no SoT mutation → the
 *       structural gate stays on the {@code NONE} floor).</li>
 * </ol>
 *
 * <p><b>Why the seam is a {@code (Town, long) → List<RoadSegment>}, not
 * {@code (Town, ServerLevel, ExpandIntent) → List<RoadTask>}.</b> The
 * {@code TickScheduler.tickRoadPlans} helper signature is
 * {@code (Town, long)} (mirroring {@code tickRaids} and {@code tickQuests},
 * which the
 * {@link org.lowern1ght.burg.tick.TickSchedulerStructuralWireTest} pins).
 * Adding {@code ServerLevel} would change every seam pin in the project;
 * adding {@code ExpandIntent} would couple the helper to the act-4 caller
 * that does not yet exist (the carve's whole point is to give that caller
 * a seam to plug into). The source interface is the abstraction boundary:
 * the production caller wraps {@code RoadBuilder.planTasks} into a
 * {@code RoadPlanSource} (or any function that returns segments), and the
 * tick helper consumes the result with no MC-typed parameters.
 *
 * <p><b>Empty list vs {@code null}.</b> The helper treats {@code null}
 * the same as an empty list (no SoT mutation, returns {@code false}).
 * Sources that have nothing to plan return {@link List#of()} rather than
 * {@code null} to keep the no-mutation path obvious at the call site.
 *
 * <p><b>Failure modes.</b> The tick helper wraps the source call in a
 * {@code try/catch}. A source that throws does not break the tick loop —
 * the exception is logged, {@link TickScheduler#tickRoadPlans(Town, long)}
 * returns {@code false}, the {@code LevelTowns.markDirty()} branch is
 * not taken, and the next tick retries with whatever state the source is
 * in by then. The seam is deliberately total: a partial / buggy source
 * degrades to no-op rather than tearing the SoT.
 */
@FunctionalInterface
public interface RoadPlanSource {

    /**
     * Plan the road segments this source wants to commit for {@code town}
     * at {@code gameTime}. Returns {@code null} or an empty list when the
     * source has nothing to plan for this tick (the no-mutation path).
     *
     * <p>Called once per town per tick from
     * {@link TickScheduler#tickRoadPlans(Town, long)}. The implementation
     * may consult {@code town.getPlannedRoads()} (idempotency), throttle
     * on {@code gameTime} (rate-limited emission), or read other
     * per-town state — anything a planner-side helper would normally
     * have.
     *
     * @param town the per-town facade the helper is iterating; never {@code null}
     * @param gameTime the current server {@code gameTime}; passed in so
     *        rate-limited sources can compute their next-fire window
     * @return the segments to commit (each will go through
     *         {@link Town#addRoadSegment(RoadSegment)}); {@code null} or
     *         empty means "no-op this tick"
     */
    List<RoadSegment> planFor(Town town, long gameTime);

    /**
     * The default source when no production caller has wired one in:
     * empty list, no SoT mutation, the helper returns {@code false} and
     * the caller skips its {@code LevelTowns.markDirty()} branch. Matches
     * the pre-carve no-op stub behaviour so the structural gate stays
     * on the {@code NONE} floor until the production caller lands.
     */
    RoadPlanSource NONE = (town, gameTime) -> List.of();
}
