package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SimpleStateMachine {

    public enum State { IDLE, BUILD }

    private static final int MAX_BUILDINGS = 50;
    // Number of consecutive failed placement ticks before a connection point is permanently killed.
    // Set to 5 so terrain obstacles are consistently impassable before giving up.
    // Previously 1 -- the retry branch was effectively unreachable, killing roads after a single bad tick.
    private static final int MAX_FAIL_COUNT = 5;
    // Ticks the NPC waits in IDLE state before attempting to queue the next building.
    private static final int IDLE_WAIT_TICKS = 200;
    private static final Logger LOGGER = LoggerFactory.getLogger(SimpleStateMachine.class);

    // Production bonus applied to every building placed during the bootstrap phase.
    private static final double BOOTSTRAP_PRODUCTION_MULTIPLIER = 1.15;

    private final Npc npc;
    private State current = State.IDLE;
    private int idleTimer = 0;
    private BuildGoal activeBuild = null;
    // Tick counter for the current BUILD state, used to log periodic stuck-detection messages.
    private int buildStateTicks = 0;
    // True when activeBuild was started from the bootstrap queue; triggers the orientation bonus on completion.
    private boolean pendingBootstrapBonus = false;

    // -------------------------------------------------------------------------
    // Placement outcome: carries either a valid result or the reason for failure.
    // Used throughout tickOpenPhase to aggregate diagnostic info before logging.
    // -------------------------------------------------------------------------
    private enum FailReason {
        NO_COMPATIBLE_CONNECTOR, // candidate has no jigsaw connector matching the connection point pool
        BOUNDING_BOX_OVERLAP     // computed BB intersects an already-occupied zone
    }

    private record PlacementSuccess(BlockPos pos, Rotation rotation, BlockPos entryConnectorWorldPos) {}

    private record PlacementOutcome(PlacementSuccess success, FailReason failure) {
        static PlacementOutcome ok(BlockPos pos, Rotation rotation, BlockPos entryPos) {
            return new PlacementOutcome(new PlacementSuccess(pos, rotation, entryPos), null);
        }
        static PlacementOutcome fail(FailReason reason) {
            return new PlacementOutcome(null, reason);
        }
        boolean succeeded() { return success != null; }
    }

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
        if (++idleTimer < IDLE_WAIT_TICKS) return;
        idleTimer = 0;
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;

        Town town = findTown(serverLevel);
        if (town == null) {
            LOGGER.warn("[OUAT] NPC {} has no linked town -- cannot build", npc.getUUID());
            return;
        }

        if (town.getBuildings().size() >= MAX_BUILDINGS) {
            LOGGER.debug("[OUAT] Town '{}' reached MAX_BUILDINGS={}, NPC idle", town.getName(), MAX_BUILDINGS);
            return;
        }

        // Initialize bootstrap queue on first ever tick for this village.
        if (!town.isBootstrapInitialized()) {
            if (town.initBootstrap()) {
                LevelTowns.get(serverLevel).markDirty();
            }
        }

        List<ConnectionPoint> freePoints = town.getAvailableConnectionPoints();
        if (freePoints.isEmpty()) {
            return;
        }

        List<BoundingBox> occupied = town.getOccupiedBoxes();
        List<ConnectionPoint> shuffledPoints = new ArrayList<>(freePoints);

        if (town.isBootstrapping()) {
            tickBootstrap(serverLevel, town, shuffledPoints, occupied);
        } else {
            tickOpenPhase(serverLevel, town, shuffledPoints, occupied);
        }
    }

    // Pool name used to identify street buildings; kept as a constant to avoid scattered literals.
    private static final String STREETS_POOL = "onceuponatown:streets";

    // Tries to place the next eligible building from the bootstrap queue at zero cost.
    // Iterates queue items and skips any whose pool has no compatible connection point this tick.
    // If no bootstrap building can be placed, streets are allowed to expand so that new
    // connection points become available for future bootstrap placements.
    private void tickBootstrap(ServerLevel serverLevel, Town town,
                                List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<String> queueSnapshot = new ArrayList<>(town.getBootstrapQueue());
        Set<String> tried = new HashSet<>();

        for (String buildingId : queueSnapshot) {
            if (tried.contains(buildingId)) continue;
            tried.add(buildingId);

            Optional<BuildingDef> defOpt = BuildingDataHandler.get(buildingId);
            if (defOpt.isEmpty()) {
                LOGGER.warn("[OUAT-BOOTSTRAP] Unknown building id '{}' in queue -- discarding", buildingId);
                town.consumeBootstrapItem(buildingId);
                LevelTowns.get(serverLevel).markDirty();
                continue;
            }

            BuildingDef def = defOpt.get();

            for (ConnectionPoint point : shuffledPoints) {
                if (!point.targetName().isEmpty() && !def.entryPool.equals(point.targetName())) continue;

                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, def);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    LOGGER.debug("[OUAT-BOOTSTRAP] Placing '{}' at {} via pool '{}' (bootstrap)",
                        buildingId, s.pos(), point.targetName());
                    town.useConnection(point);
                    town.consumeBootstrapItem(buildingId);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, def, point,
                        s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                        List.of(), town);
                    pendingBootstrapBonus = true;
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, buildingId, s.pos(), true);
                    return;
                }
            }
            LOGGER.debug("[OUAT-BOOTSTRAP] No compatible connection point found for '{}' this tick", buildingId);
        }

        // No bootstrap building could be placed this tick -- allow streets to expand.
        // Streets can open new connection points (jobs, houses) that unblock future bootstrap placements.
        tryPlaceStreetDuringBootstrap(serverLevel, town, shuffledPoints, occupied);
    }

    // Attempts to place a street at any available street connection point during the bootstrap phase.
    // Streets pay their normal construction cost -- no bootstrap bonus is applied.
    private void tryPlaceStreetDuringBootstrap(ServerLevel serverLevel, Town town,
                                                List<ConnectionPoint> shuffledPoints,
                                                List<BoundingBox> occupied) {
        List<BuildingDef> streetCandidates = new ArrayList<>(town.getBuildableBuildings().stream()
            .filter(d -> STREETS_POOL.equals(d.entryPool))
            .toList());

        if (streetCandidates.isEmpty()) return;

        for (ConnectionPoint point : shuffledPoints) {
            if (!STREETS_POOL.equals(point.targetName())) continue;

            shuffleInPlace(streetCandidates);
            for (BuildingDef candidate : streetCandidates) {
                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, candidate);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    LOGGER.debug("[OUAT-BOOTSTRAP] Placing street '{}' at {} to expand connection points during bootstrap",
                        candidate.id, s.pos());
                    town.useConnection(point);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, candidate, point,
                        s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                        candidate.constructionCost, town);
                    pendingBootstrapBonus = false;
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, candidate.id, s.pos(), false);
                    return;
                }
            }
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
                // No building definition targets this pool at all -- the connection is permanently useless.
                LOGGER.warn("[OUAT] CONNECTION KILLED (no def for pool) -- pos={} dir={} pool='{}' failCount={}",
                    point.pos(), point.direction(), point.targetName(), point.failCount());
                deadPoints.add(point);
                continue;
            }

            List<BuildingDef> candidates = new ArrayList<>(allBuildable.stream()
                .filter(d -> point.targetName().isEmpty() || d.entryPool.equals(point.targetName()))
                .toList());

            if (candidates.isEmpty()) {
                // Compatible buildings exist but none are affordable -- keep point alive.
                LOGGER.debug("[OUAT] Connection PENDING_RESOURCES -- pool='{}' pos={} ({} potential defs, 0 affordable)",
                    point.targetName(), point.pos(), allPotential.size());
                continue;
            }

            shuffleInPlace(candidates);
            boolean physicalFailure = false;
            // Collect per-candidate failure reasons to produce a useful summary if all fail.
            Map<String, FailReason> failureLog = new HashMap<>();

            for (BuildingDef candidate : candidates) {
                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, candidate);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    LOGGER.debug("[OUAT] Starting build '{}' at {} via pool '{}'",
                        candidate.id, s.pos(), point.targetName());
                    town.useConnection(point);
                    cleanupDeadPoints(town, serverLevel, deadPoints, toIncrementFail);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, candidate, point,
                        s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                        candidate.constructionCost, town);
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, candidate.id, s.pos(), false);
                    return;
                }
                physicalFailure = true;
                failureLog.put(candidate.id, outcome.failure());
            }

            if (physicalFailure) {
                int nextFail = point.failCount() + 1;
                if (nextFail >= MAX_FAIL_COUNT) {
                    // Log a summary of why every candidate failed before killing the point.
                    StringBuilder sb = new StringBuilder();
                    failureLog.forEach((id, reason) ->
                        sb.append("\n    ").append(id).append(" -> ").append(reason));
                    LOGGER.warn("[OUAT] CONNECTION KILLED (physical failures exhausted) -- pos={} dir={} pool='{}' failCount={}/{}  candidates:{}",
                        point.pos(), point.direction(), point.targetName(),
                        nextFail, MAX_FAIL_COUNT, sb);
                    deadPoints.add(point);
                } else {
                    // Log per-candidate failures at debug so retries can be tracked.
                    StringBuilder sb = new StringBuilder();
                    failureLog.forEach((id, reason) ->
                        sb.append("\n    ").append(id).append(" -> ").append(reason));
                    LOGGER.debug("[OUAT] Connection physical failure {}/{} -- pos={} pool='{}' candidates:{}",
                        nextFail, MAX_FAIL_COUNT, point.pos(), point.targetName(), sb);
                    toIncrementFail.add(point);
                }
            }
        }

        cleanupDeadPoints(town, serverLevel, deadPoints, toIncrementFail);
    }

    // Attempts to find a valid placement for a building at a given connection point.
    // Returns a PlacementOutcome carrying either a valid result or the specific failure reason.
    private PlacementOutcome attemptPlacement(ServerLevel serverLevel, ConnectionPoint point,
                                               List<BoundingBox> occupied,
                                               BuildingDef def) {
        List<JigsawConnector> connectors = BuildSchematic.readConnectors(serverLevel, def.nbt);
        List<JigsawConnector> compatible = connectors.stream()
            .filter(c -> point.targetName().isEmpty() || c.name().equals(point.targetName()))
            .toList();
        if (compatible.isEmpty()) return PlacementOutcome.fail(FailReason.NO_COMPATIBLE_CONNECTOR);

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
            if (overlaps) return PlacementOutcome.fail(FailReason.BOUNDING_BOX_OVERLAP);
        }

        BlockPos entryConnectorWorldPos = finalPos.offset(
            StructureTemplate.transform(chosen.posInTemplate(), Mirror.NONE, rotation, BlockPos.ZERO));

        return PlacementOutcome.ok(finalPos, rotation, entryConnectorWorldPos);
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

    private <T> void shuffleInPlace(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = npc.getRandom().nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private void tickBuild() {
        if (activeBuild == null) { current = State.IDLE; pendingBootstrapBonus = false; buildStateTicks = 0; return; }
        buildStateTicks++;
        // Log every 400t while in BUILD state so we can detect an NPC that never finishes.
        if (buildStateTicks % 400 == 0) {
            LOGGER.warn("[OUAT-SM] NPC {} has been in BUILD state for {}t -- building='{}' targetPos={}",
                npc.getUUID(), buildStateTicks, activeBuild.getDefId(), activeBuild.getFinalPlacementPos());
        }
        if (activeBuild.tick()) {
            LOGGER.debug("[OUAT-SM] NPC {} BUILD complete -- building='{}' failed={} buildStateTicks={}",
                npc.getUUID(), activeBuild.getDefId(), activeBuild.isFailed(), buildStateTicks);
            if (!activeBuild.isFailed() && pendingBootstrapBonus) {
                applyBootstrapBonus();
            }
            pendingBootstrapBonus = false;
            activeBuild = null;
            buildStateTicks = 0;
            current = State.IDLE;
        }
    }

    // Marks the most recently registered building with the orientation production bonus.
    // Called after a successful bootstrap build completes.
    private void applyBootstrapBonus() {
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;
        Town town = findTown(serverLevel);
        if (town == null || town.getBuildings().isEmpty()) return;
        PlacedBuilding lastBuilt = town.getBuildings().get(town.getBuildings().size() - 1);
        lastBuilt.setInstanceProductionMultiplier(BOOTSTRAP_PRODUCTION_MULTIPLIER);
        LevelTowns.get(serverLevel).markDirty();
        LOGGER.debug("[OUAT-BOOTSTRAP] Applied {}x orientation bonus to building='{}'",
            BOOTSTRAP_PRODUCTION_MULTIPLIER, lastBuilt.getDefId());
    }

    // Sends a chat message to all players when the NPC starts a new construction task.
    // 'bootstrap' flag adds a visual cue that this is a free orientation-bonus build.
    private void broadcastBuildStart(ServerLevel level, String buildingId, BlockPos pos, boolean bootstrap) {
        String label = bootstrap ? "[Village - Bootstrap] " : "[Village] ";
        ChatFormatting labelColor = bootstrap ? ChatFormatting.AQUA : ChatFormatting.GOLD;
        Component msg = Component.literal(label)
            .withStyle(labelColor)
            .append(Component.literal("Builder started: ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(buildingId)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" @ " + pos.toShortString())
                .withStyle(ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
        LOGGER.info("[OUAT] Build started: building='{}' pos={} bootstrap={}", buildingId, pos, bootstrap);
    }

    private Town findTown(ServerLevel level) {
        List<Town> allTowns = new java.util.ArrayList<>(LevelTowns.get(level).getAllTowns());
        Town found = allTowns.stream()
            .filter(t -> npc.getUUID().equals(t.getBuilderNpcId()))
            .findFirst().orElse(null);

        if (found == null) {
            // DEBUG: dump all known builderNpcIds to understand why no town matches this NPC
            StringBuilder sb = new StringBuilder();
            for (Town t : allTowns) {
                sb.append("\n    town='").append(t.getName())
                  .append("' builderNpcId=").append(t.getBuilderNpcId());
            }
            LOGGER.warn("[OUAT-DEBUG] NPC {} (this uuid) matched NO town. Known towns ({}):{} ",
                    npc.getUUID(), allTowns.size(), sb);
        }

        return found;
    }
}
