package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.schematic.JigsawConnector;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SimpleStateMachine {

    public enum State { IDLE, BUILD }

    private static final int MAX_BUILDINGS = 50;
    private static final int MAX_FAIL_COUNT = 1;
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleStateMachine.class);

    private final Npc npc;
    private State current = State.IDLE;
    private int idleTimer = 0;
    private BuildGoal activeBuild = null;

    private record PlacementResult(BlockPos pos, Rotation rotation, BlockPos entryConnectorWorldPos) {}

    public SimpleStateMachine(Npc npc) {
        this.npc = npc;
    }

    public State getState() { return current; }

    public void tick() {
        switch (current) {
            case IDLE -> tickIdle();
            case BUILD -> tickBuild();
        }
    }

    private void tickIdle() {
        if (++idleTimer < 200) return;
        idleTimer = 0;
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;

        Town town = findTown(serverLevel);
        if (town == null) return;

        if (town.getBuildings().size() >= MAX_BUILDINGS) return;

        // Initialize bootstrap queue on first ever tick for this village.
        if (!town.isBootstrapInitialized()) {
            if (town.initBootstrap(npc.getRandom())) {
                LevelTowns.get(serverLevel).markDirty();
            }
        }

        List<ConnectionPoint> freePoints = town.getAvailableConnectionPoints();
        if (freePoints.isEmpty()) return;

        List<BoundingBox> occupied = town.getOccupiedBoxes();
        List<ConnectionPoint> shuffledPoints = new ArrayList<>(freePoints); // oldest first — preserves insertion order

        // Streets are regular candidates — no special priority over buildings.
        if (town.isBootstrapping()) {
            tickBootstrap(serverLevel, town, shuffledPoints, occupied);
        } else {
            tickOpenPhase(serverLevel, town, shuffledPoints, occupied);
        }
    }

    // Tries to place the next eligible building from the bootstrap queue at zero cost.
    // Iterates queue items and skips any whose pool has no compatible connection point this tick.
    private void tickBootstrap(ServerLevel serverLevel, Town town,
                                List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<String> queueSnapshot = new ArrayList<>(town.getBootstrapQueue());
        Set<String> tried = new HashSet<>();

        for (String buildingId : queueSnapshot) {
            if (tried.contains(buildingId)) continue;
            tried.add(buildingId);

            Optional<BuildingDef> defOpt = BuildingDataHandler.get(buildingId);
            if (defOpt.isEmpty()) {
                // Unknown ID in queue (data was removed) - discard it.
                town.consumeBootstrapItem(buildingId);
                LevelTowns.get(serverLevel).markDirty();
                continue;
            }

            BuildingDef def = defOpt.get();

            for (ConnectionPoint point : shuffledPoints) {
                if (!point.targetName().isEmpty() && !def.entryPool.equals(point.targetName())) continue;

                PlacementResult result = attemptPlacement(serverLevel, point, occupied, def);
                if (result != null) {
                    // Bootstrap buildings are placed at zero cost - pass empty cost list.
                    town.useConnection(point);
                    town.consumeBootstrapItem(buildingId);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, def, point,
                        result.pos(), result.rotation(), result.entryConnectorWorldPos(),
                        List.of(), town);
                    current = State.BUILD;
                    return;
                }
            }
            // No compatible point found this tick for this queue item -> skip to next item.
        }
    }

    // Open phase: random building selection (streets included as equal candidates).
    // A point is kept alive (PENDING_RESOURCES) if a compatible building exists but is unaffordable.
    // A point is marked dead only when no compatible building exists at all, or after MAX_FAIL_COUNT physical failures.
    private void tickOpenPhase(ServerLevel serverLevel, Town town,
                                List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<BuildingDef> allBuildable = new ArrayList<>(town.getBuildableBuildings());

        List<ConnectionPoint> deadPoints = new ArrayList<>();
        List<ConnectionPoint> toIncrementFail = new ArrayList<>();

        for (ConnectionPoint point : shuffledPoints) {
            List<BuildingDef> allPotential = town.getPotentialBuildings(point);

            if (allPotential.isEmpty()) {
                deadPoints.add(point);
                continue;
            }

            List<BuildingDef> candidates = new ArrayList<>(allBuildable.stream()
                .filter(d -> point.targetName().isEmpty() || d.entryPool.equals(point.targetName()))
                .toList());

            if (candidates.isEmpty()) {
                // Compatible buildings exist but none are affordable -> PENDING_RESOURCES, retry later.
                continue;
            }

            shuffleInPlace(candidates);
            boolean physicalFailure = false;

            for (BuildingDef candidate : candidates) {
                PlacementResult result = attemptPlacement(serverLevel, point, occupied, candidate);
                if (result != null) {
                    town.useConnection(point);
                    cleanupDeadPoints(town, serverLevel, deadPoints, toIncrementFail);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, candidate, point,
                        result.pos(), result.rotation(), result.entryConnectorWorldPos(),
                        candidate.constructionCost, town);
                    current = State.BUILD;
                    return;
                }
                physicalFailure = true;
            }

            if (physicalFailure) {
                if (point.failCount() + 1 >= MAX_FAIL_COUNT) {
                    deadPoints.add(point);
                } else {
                    toIncrementFail.add(point);
                }
            }
        }

        cleanupDeadPoints(town, serverLevel, deadPoints, toIncrementFail);
    }

    // Attempts to find a valid placement for a building at a given connection point.
    // Returns null if no valid placement exists (no compatible connector or overlap).
    private PlacementResult attemptPlacement(ServerLevel serverLevel, ConnectionPoint point,
                                              List<BoundingBox> occupied, BuildingDef def) {
        List<JigsawConnector> connectors = BuildSchematic.readConnectors(serverLevel, def.nbt);
        List<JigsawConnector> compatible = connectors.stream()
            .filter(c -> point.targetName().isEmpty() || c.name().equals(point.targetName()))
            .toList();
        if (compatible.isEmpty()) return null;

        JigsawConnector chosen = compatible.get(npc.getRandom().nextInt(compatible.size()));
        Rotation rotation = BuildSchematic.computeRequiredRotation(
            chosen.facing(), point.direction().getOpposite());
        BlockPos rawPos = BuildSchematic.computeCandidatePosition(
            point.pos(), point.direction(), chosen.posInTemplate(), rotation);

        // Snap Y to actual terrain at the attach point so the entry jigsaw lands on real ground.
        BlockPos attachPoint = point.pos().relative(point.direction());
        int terrainY = BuildSchematic.findGroundY(serverLevel, attachPoint);
        int finalY = terrainY - chosen.posInTemplate().getY();
        BlockPos finalPos = new BlockPos(rawPos.getX(), finalY, rawPos.getZ());

        Optional<BoundingBox> maybeBb = BuildSchematic.computeBoundingBox(
            serverLevel, finalPos, def.nbt, rotation);
        if (maybeBb.isPresent()) {
            BoundingBox cb = maybeBb.get();
            boolean overlaps = occupied.stream().anyMatch(bb ->
                bb.minX() < cb.maxX() && bb.maxX() > cb.minX() &&
                bb.minZ() < cb.maxZ() && bb.maxZ() > cb.minZ()
            );
            if (overlaps) return null;
        }

        BlockPos entryConnectorWorldPos = finalPos.offset(
            StructureTemplate.transform(chosen.posInTemplate(), Mirror.NONE, rotation, BlockPos.ZERO));

        return new PlacementResult(finalPos, rotation, entryConnectorWorldPos);
    }

    private void cleanupDeadPoints(Town town, ServerLevel serverLevel,
                                    List<ConnectionPoint> deadPoints, List<ConnectionPoint> toIncrementFail) {
        boolean dirty = false;
        if (!deadPoints.isEmpty()) {
            deadPoints.forEach(town::useConnection);
            dirty = true;
        }
        if (!toIncrementFail.isEmpty()) {
            toIncrementFail.forEach(town::incrementConnectionFailCount);
            dirty = true;
        }
        if (dirty) LevelTowns.get(serverLevel).markDirty();
    }

    private <T> List<T> shuffled(List<T> source) {
        List<T> copy = new ArrayList<>(source);
        shuffleInPlace(copy);
        return copy;
    }

    private <T> void shuffleInPlace(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = npc.getRandom().nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private void tickBuild() {
        if (activeBuild == null) { current = State.IDLE; return; }
        if (activeBuild.tick()) {
            ConnectionPoint used = activeBuild.getUsedConnection();
            if (activeBuild.isFailed()) {
                LOGGER.debug("Build failed (3min timeout): connection={} dropped permanently", used.targetName());
            }
            activeBuild = null;
            current = State.IDLE;
        }
    }

    private Town findTown(ServerLevel level) {
        return LevelTowns.get(level).getAllTowns().stream()
            .filter(t -> npc.getUUID().equals(t.getBuilderNpcId()))
            .findFirst().orElse(null);
    }
}
