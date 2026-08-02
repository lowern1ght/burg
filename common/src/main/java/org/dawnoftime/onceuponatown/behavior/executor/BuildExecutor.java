package org.dawnoftime.onceuponatown.behavior.executor;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.town.Town;

/**
 * Seam for build-queue operations.
 *
 * <p>The behavior engine ({@link org.dawnoftime.onceuponatown.behavior.BehaviorEngine}) drives
 * {@link org.dawnoftime.onceuponatown.behavior.task.BuildTask} and
 * {@link org.dawnoftime.onceuponatown.behavior.task.UpgradeTask}. Those tasks need to talk to
 * whatever owns the construction queue, but they should not depend on the full surface of
 * {@link Town}. This interface is the contract they call against.
 *
 * <p>Production wiring: {@link Town} implements this directly (see its wrapper methods at
 * {@code Town#tryQueueNewBuild}, {@code Town#tryQueueUpgrade}, {@code Town#isPlaced}).
 *
 * <p>Test wiring: {@code gametest.FakeBuildExecutor} records every call and returns
 * configurable results, so the engine can be exercised without a real construction queue.
 */
public interface BuildExecutor {

    /**
     * Queue a new build. Returns true if accepted (slot available, def exists, resources
     * affordable, weight cap not exceeded, etc.).
     *
     * <p>The {@code placerNpcUuid} identifies the NPC that will execute. The current
     * implementation lets the underlying town assign the builder internally; the UUID is
     * accepted for forward compatibility (a future revision of Town may reserve the slot
     * for the named NPC).
     */
    boolean tryQueueNewBuild(Town town, ResourceLocation buildingDefId, String placerNpcUuid);

    /**
     * Queue an upgrade for an already-placed building. The target level is derived
     * internally by the underlying town (current level + 1).
     */
    boolean tryQueueUpgrade(Town town, BlockPos buildingPos, String placerNpcUuid);

    /**
     * Has a building with this {@code buildingDefId} been placed in the town?
     *
     * <p>Matches by {@link ResourceLocation#getPath()}: Town stores defs by their bare path
     * (e.g. {@code "settlement"}), and the engine's intent ids use full resource locations.
     * Comparing on the path keeps the two representations aligned.
     */
    boolean isPlaced(Town town, ResourceLocation buildingDefId);
}
