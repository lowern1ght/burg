package org.lowern1ght.burg.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.behavior.executor.BuildExecutor;
import org.lowern1ght.burg.town.Town;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Test double for {@link BuildExecutor}.
 *
 * <p>Records every call and returns configurable results so the behaviour engine can be
 * exercised in a GameTest without standing up a real construction queue. Lives in
 * {@code gametest/} alongside the {@code BehaviorEngineGameTest} because the project's
 * plain-JVM test source set explicitly forbids Minecraft on its classpath
 * ({@code common/build.gradle} line 14), so anything that needs {@code Town} or
 * {@code ResourceLocation} must live in a GameTest.
 *
 * <p>Tests should reset the record lists between cases (or use a fresh instance).
 */
public final class FakeBuildExecutor implements BuildExecutor {

    public record NewBuildCall(Town town, ResourceLocation defId, String placerUuid) {}
    public record UpgradeCall(Town town, BlockPos pos, String placerUuid) {}
    public record PlacedCheck(Town town, ResourceLocation defId) {}

    private final List<NewBuildCall> newBuildCalls = new ArrayList<>();
    private final List<UpgradeCall> upgradeCalls = new ArrayList<>();
    private final List<PlacedCheck> placedChecks = new ArrayList<>();
    /** Returned by {@link #tryQueueNewBuild}. Tests flip this to false to simulate rejection. */
    public boolean newBuildResult = true;
    /** Returned by {@link #tryQueueUpgrade}. Tests flip this to false to simulate rejection. */
    public boolean upgradeResult = true;
    /** ResourceLocations reported as placed by {@link #isPlaced}. Tests add to this set. */
    public final Set<ResourceLocation> placed = new HashSet<>();

    @Override
    public boolean tryQueueNewBuild(Town town, ResourceLocation buildingDefId, String placerNpcUuid) {
        newBuildCalls.add(new NewBuildCall(town, buildingDefId, placerNpcUuid));
        return newBuildResult;
    }

    @Override
    public boolean tryQueueUpgrade(Town town, BlockPos buildingPos, String placerNpcUuid) {
        upgradeCalls.add(new UpgradeCall(town, buildingPos, placerNpcUuid));
        return upgradeResult;
    }

    @Override
    public boolean isPlaced(Town town, ResourceLocation buildingDefId) {
        placedChecks.add(new PlacedCheck(town, buildingDefId));
        return placed.contains(buildingDefId);
    }

    /** All recorded {@link #tryQueueNewBuild} calls, in invocation order. */
    public List<NewBuildCall> getNewBuildCalls() { return List.copyOf(newBuildCalls); }

    /** All recorded {@link #tryQueueUpgrade} calls, in invocation order. */
    public List<UpgradeCall> getUpgradeCalls() { return List.copyOf(upgradeCalls); }

    /** All recorded {@link #isPlaced} checks, in invocation order. */
    public List<PlacedCheck> getPlacedChecks() { return List.copyOf(placedChecks); }

    /** Reset all record lists. Useful between tests sharing one FakeBuildExecutor. */
    public void reset() {
        newBuildCalls.clear();
        upgradeCalls.clear();
        placedChecks.clear();
        placed.clear();
        newBuildResult = true;
        upgradeResult = true;
    }
}
