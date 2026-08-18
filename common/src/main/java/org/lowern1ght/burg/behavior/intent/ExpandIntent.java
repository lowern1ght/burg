package org.lowern1ght.burg.behavior.intent;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.town.Town;

import java.util.Set;

/**
 * Intent to lay a road (or extend the network) between two world positions.
 *
 * <p>{@code from} and {@code to} are the endpoints of the desired road
 * segment. The planner fills in the waypoints between them; the engine
 * never sees the waypoints at the intent layer.
 *
 * <p>This is a breaking change relative to the Phase-1 stub, which carried
 * a single {@code targetDefId}. The id is now derived from {@code from} and
 * {@code to} so the scheduler can dedupe identical requests; the planner
 * still has the full segment data to do its job.
 *
 * <p>Planning-only slice: {@link #canResolve} and {@link #isStillValid}
 * are conservative — the engine will pair an intent with a free builder
 * whenever the town has one, and the planner's failure modes (no path
 * found, malformed endpoints) are folded into the resulting
 * {@link org.lowern1ght.burg.behavior.task.RoadTask} lifecycle.
 */
public record ExpandIntent(
        BlockPos from,
        BlockPos to,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        // A stable id derived from the endpoints. Two ExpandIntents with the
        // same from/to in the same town collapse to the same id, which is
        // the scheduler's deduping key.
        return ResourceLocation.fromNamespaceAndPath(
            Constants.MOD_ID,
            "expand/" + Long.toHexString(from.asLong()) + "_" + Long.toHexString(to.asLong())
        );
    }

    @Override
    public Town town() {
        return town;
    }

    @Override
    public int basePriority() {
        return priority;
    }

    @Override
    public IntentCost cost() {
        return cost;
    }

    @Override
    public Set<CitizenRole> requiredRoles() {
        return Set.of(CitizenRole.ROAD_BUILDER, CitizenRole.BUILDER);
    }

    @Override
    public boolean canResolve(Town town) {
        // The planner can run any time there is a free builder. The town
        // cannot resolve itself — the engine owns the citizen lookup.
        return town != null && town == this.town;
    }

    @Override
    public boolean isStillValid(Town town) {
        // The intent is valid as long as the town is still alive and the
        // endpoints are distinct. Deeper validity (chunks loaded, no
        // already-placed building in the way) is the planner's job.
        return town != null && town == this.town && !from.equals(to);
    }
}
