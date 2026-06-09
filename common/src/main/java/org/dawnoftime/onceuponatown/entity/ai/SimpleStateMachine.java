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
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.QueueEntry;
import org.dawnoftime.onceuponatown.town.Town;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SimpleStateMachine {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleStateMachine.class);

    public enum State { IDLE, BUILD }

    private static final int MAX_BUILDINGS = 50;
    // Number of consecutive failed placement ticks before a connection point is permanently killed.
    // Set to 5 so terrain obstacles are consistently impassable before giving up.
    // Previously 1 -- the retry branch was effectively unreachable, killing roads after a single bad tick.
    private static final int MAX_FAIL_COUNT = 5;
    // Ticks the NPC waits in IDLE state before attempting to queue the next building.
    private static final int IDLE_WAIT_TICKS = 200;
    // When no player queue, the NPC picks a random building once per minute to expand autonomously.
    private static final int OPEN_PHASE_TICKS = 1200;

    // Production bonus applied to orientation-boosted buildings when built by the player.
    private static final double ORIENTATION_BONUS_MULTIPLIER = 1.15;

    private final Npc npc;
    private State current = State.IDLE;
    private int idleTimer = 0;
    private BuildTask activeBuild = null;
    private int buildStateTicks = 0;
    // The player-queued entry currently being built, or null if not building from queue.
    private QueueEntry activeQueueEntry = null;
    // Number of consecutive idle ticks where the current queue cursor entry could not be placed.
    private int playerQueueFailCount = 0;
    // Whether the saved-build resume check has been attempted this session.
    private boolean resumeChecked = false;
    // Counts ticks in the open phase (no player queue) toward OPEN_PHASE_TICKS.
    private int openPhaseTimer = 0;

    // -------------------------------------------------------------------------
    // Placement outcome: carries either a valid result or the reason for failure.
    // Used throughout tickOpenPhase to aggregate diagnostic info before logging.
    // -------------------------------------------------------------------------
    private enum FailReason {
        NO_COMPATIBLE_CONNECTOR, // candidate has no jigsaw connector matching the connection point pool
        BOUNDING_BOX_OVERLAP     // computed BB intersects an already-occupied zone
    }

    private record PlacementSuccess(BlockPos pos, Rotation rotation, BlockPos entryConnectorWorldPos, BoundingBox bb) {}

    private record PlacementOutcome(PlacementSuccess success, FailReason failure) {
        static PlacementOutcome ok(BlockPos pos, Rotation rotation, BlockPos entryPos, BoundingBox bb) {
            return new PlacementOutcome(new PlacementSuccess(pos, rotation, entryPos, bb), null);
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
        // On the very first idle tick, attempt to resume an in-progress build from before a restart.
        if (!resumeChecked) {
            resumeChecked = true;
            net.minecraft.nbt.CompoundTag savedBuild = npc.pollPendingBuildResume();
            if (savedBuild != null && npc.level() instanceof ServerLevel resumeLevel) {
                Town resumeTown = findTown(resumeLevel);
                if (resumeTown != null) {
                    BuildGoal resumed = BuildGoal.resumeFrom(savedBuild, npc, resumeTown, resumeLevel);
                    if (resumed != null) {
                        activeBuild = resumed;
                        activeQueueEntry = savedBuild.contains("queue_def_id")
                            ? new QueueEntry.NewBuild(savedBuild.getString("queue_def_id")) : null;
                        current = State.BUILD;
                        return;
                    }
                }
            }
        }

        if (++idleTimer < IDLE_WAIT_TICKS) return;
        idleTimer = 0;
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;

        Town town = findTown(serverLevel);
        if (town == null) {
            if (idleTimer == 1)
                LOGGER.warn("[OUAT-NPC] {} cannot find its town. AnchorPos: {}",
                    npc.getUUID(), npc.getTownAnchorPos());
            return;
        }

        if (town.getBuildings().size() >= MAX_BUILDINGS) {
            return;
        }

        List<ConnectionPoint> freePoints = town.getAvailableConnectionPoints();
        if (freePoints.isEmpty()) {
            return;
        }

        List<BoundingBox> occupied = town.getOccupiedBoxes();
        List<ConnectionPoint> shuffledPoints = new ArrayList<>(freePoints);

        if (!town.getConstructionQueue().isEmpty()) {
            openPhaseTimer = 0;
            tickPlayerQueue(serverLevel, town, shuffledPoints, occupied);
            // If the queue building could not be placed this tick, still expand streets
            // so the village does not stall while waiting for compatible connection points.
            if (current == State.IDLE) {
                tickStreetsOnly(serverLevel, town, shuffledPoints, occupied);
            }
        } else {
            // No player instructions: extend roads once per minute.
            if (++openPhaseTimer >= OPEN_PHASE_TICKS) {
                openPhaseTimer = 0;
                tickStreetsOnly(serverLevel, town, shuffledPoints, occupied);
            }
        }
    }

    // Pool name used to identify street buildings; kept as a constant to avoid scattered literals.
    private static final String STREETS_POOL = "onceuponatown:streets";

    // Player-directed queue: tries to process the entry at the current cursor position.
    // Upgrades are dispatched immediately to UpgradeGoal (no connection point needed).
    // New builds try each connection point and advance the cursor on MAX_FAIL_COUNT failures.
    private void tickPlayerQueue(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<QueueEntry> queue = town.getConstructionQueue();
        if (queue.isEmpty()) return;

        int cursor = town.getConstructionQueueCursor();
        if (cursor >= queue.size()) {
            town.resetQueueCursor();
            cursor = 0;
        }

        QueueEntry entry = queue.get(cursor);

        // Upgrade entries: walk to the building and run the visual diff.
        if (entry instanceof QueueEntry.Upgrade upgradeEntry) {
            PlacedBuilding building = town.getBuildings().stream()
                .filter(b -> b.worldPos.equals(upgradeEntry.buildingWorldPos()))
                .findFirst().orElse(null);
            BuildingDef def = BuildingDataHandler.get(upgradeEntry.defId()).orElse(null);

            if (building == null || def == null) {
                town.consumeQueueEntry(entry);
                LevelTowns.get(serverLevel).markDirty();
                return;
            }

            activeBuild = new BuildGoal(npc, new UpgradeAction(building, def, upgradeEntry.fromLevel(), town));
            activeQueueEntry = entry;
            playerQueueFailCount = 0;
            current = State.BUILD;
            broadcastUpgradeStart(serverLevel, upgradeEntry.defId(), upgradeEntry.buildingWorldPos());
            NetworkHelper.pushHubToWatchers(serverLevel, town, npc.getTownAnchorPos());
            return;
        }

        // New build entries: find a compatible connection point.
        String defId = ((QueueEntry.NewBuild) entry).defId();
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) {
            town.consumeQueueEntry(entry);
            LevelTowns.get(serverLevel).markDirty();
            return;
        }

        // Prerequisites check: if the village doesn't meet them yet, skip this entry and wait.
        if (!town.meetsPrerequisites(def)) {
            LOGGER.debug("[OUAT-QUEUE] Prerequisites not met for '{}' -- skipping cursor.", defId);
            playerQueueFailCount = 0;
            town.advanceQueueCursor();
            LevelTowns.get(serverLevel).markDirty();
            return;
        }

        // Diagnostic counters to understand why placement keeps failing.
        int poolMatches = 0;
        int bbOverlaps = 0;
        int noConnector = 0;

        for (ConnectionPoint point : shuffledPoints) {
            if (!point.targetName().isEmpty() && !def.entryPool.equals(point.targetName())) continue;
            poolMatches++;
            PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, def);
            if (outcome.succeeded()) {
                PlacementSuccess s = outcome.success();
                town.useConnection(point);
                LevelTowns.get(serverLevel).markDirty();
                // Resources were pre-reserved at queue time; pass empty cost to skip re-deduction.
                activeBuild = new BuildGoal(npc, new NewBuildAction(
                    def, point, s.pos(), s.rotation(), s.entryConnectorWorldPos(), List.of(), town));
                if (s.bb() != null) town.addUnderConstruction(def.id, s.pos(), s.bb(), s.rotation());
                activeQueueEntry = entry;
                playerQueueFailCount = 0;
                current = State.BUILD;
                broadcastBuildStart(serverLevel, defId, s.pos());
                NetworkHelper.pushHubToWatchers(serverLevel, town, npc.getTownAnchorPos());
                return;
            }
            if (outcome.failure() == FailReason.BOUNDING_BOX_OVERLAP)    bbOverlaps++;
            else if (outcome.failure() == FailReason.NO_COMPATIBLE_CONNECTOR) noConnector++;
        }

        // No compatible connection point found this tick -- count as one failure.
        playerQueueFailCount++;
        if (playerQueueFailCount >= MAX_FAIL_COUNT) {
            LOGGER.warn("[OUAT-QUEUE] Placement failed for '{}' after {} attempts (cursor={}) -- "
                + "totalCPs={} poolMatches={} bbOverlap={} noConnector={} -- advancing cursor.",
                defId, MAX_FAIL_COUNT, cursor,
                shuffledPoints.size(), poolMatches, bbOverlaps, noConnector);
            playerQueueFailCount = 0;
            town.advanceQueueCursor();
            LevelTowns.get(serverLevel).markDirty();
        }
    }

    // Post-bootstrap idle phase: the builder only places streets autonomously.
    // Non-street connection points are preserved for the player construction queue.
    // Street points follow the same MAX_FAIL_COUNT kill logic as before.
    private void tickStreetsOnly(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<BuildingDef> streetCandidates = new ArrayList<>(town.getBuildableBuildings().stream()
            .filter(d -> STREETS_POOL.equals(d.entryPool))
            .toList());

        List<ConnectionPoint> deadPoints = new ArrayList<>();
        List<ConnectionPoint> toIncrementFail = new ArrayList<>();

        for (ConnectionPoint point : shuffledPoints) {
            if (!STREETS_POOL.equals(point.targetName())) continue;

            if (streetCandidates.isEmpty()) break;

            shuffleInPlace(streetCandidates);
            boolean physicalFailure = false;

            for (BuildingDef candidate : streetCandidates) {
                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, candidate);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    town.useConnection(point);
                    cleanupDeadPoints(town, serverLevel, deadPoints, toIncrementFail);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, new NewBuildAction(
                        candidate, point, s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                        candidate.constructionCost, town));
                    if (s.bb() != null) town.addUnderConstruction(candidate.id, s.pos(), s.bb(), s.rotation());
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, candidate.id, s.pos());
                    NetworkHelper.pushHubToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return;
                }
                physicalFailure = true;
            }

            if (physicalFailure) {
                int nextFail = point.failCount() + 1;
                if (nextFail >= MAX_FAIL_COUNT) {
                    deadPoints.add(point);
                } else {
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

        return PlacementOutcome.ok(finalPos, rotation, entryConnectorWorldPos, maybeBb.orElse(null));
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
        if (activeBuild == null) { current = State.IDLE; buildStateTicks = 0; return; }
        buildStateTicks++;
        if (activeBuild.tick()) {
            BlockPos completedPos = activeBuild.getFinalPlacementPos();
            if (!activeBuild.isFailed() && activeQueueEntry != null) {
                if (npc.level() instanceof ServerLevel sl) {
                    Town qTown = findTown(sl);
                    if (qTown != null) {
                        qTown.consumeQueueEntry(activeQueueEntry);
                        // Apply orientation bonus when player builds a boosted building.
                        if (activeQueueEntry instanceof QueueEntry.NewBuild nb
                                && qTown.getBoostedBuildingIds().contains(nb.defId())
                                && !qTown.getBuildings().isEmpty()) {
                            qTown.getBuildings().get(qTown.getBuildings().size() - 1)
                                .setInstanceProductionMultiplier(ORIENTATION_BONUS_MULTIPLIER);
                        }
                        LevelTowns.get(sl).markDirty();
                    }
                }
            }
            // Remove construction/upgrade markers regardless of success or failure.
            if (npc.level() instanceof ServerLevel sl) {
                Town doneTown = findTown(sl);
                if (doneTown != null) {
                    doneTown.removeUnderConstruction(completedPos);
                    doneTown.removeUnderUpgrade(completedPos);
                    NetworkHelper.pushHubToWatchers(sl, doneTown, npc.getTownAnchorPos());
                }
            }
            activeQueueEntry = null;
            activeBuild = null;
            buildStateTicks = 0;
            current = State.IDLE;
        }
    }

    private void broadcastBuildStart(ServerLevel level, String buildingId, BlockPos pos) {
        Component msg = Component.literal("[Village] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Builder started: ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(buildingId)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" @ " + pos.toShortString())
                .withStyle(ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private void broadcastUpgradeStart(ServerLevel level, String buildingId, BlockPos pos) {
        Component msg = Component.literal("[Village] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Upgrading: ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(buildingId)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" @ " + pos.toShortString())
                .withStyle(ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    // Serializes the active BUILDING state into the NPC's save tag so it survives server restarts.
    // UpgradeGoal.saveTo() is a no-op, so upgrade tasks resume from scratch after restart.
    public void saveBuildState(net.minecraft.nbt.CompoundTag tag) {
        if (activeBuild == null || current != State.BUILD) return;
        net.minecraft.nbt.CompoundTag buildTag = new net.minecraft.nbt.CompoundTag();
        activeBuild.saveTo(buildTag);
        if (buildTag.isEmpty()) return;
        if (activeQueueEntry instanceof QueueEntry.NewBuild nb) buildTag.putString("queue_def_id", nb.defId());
        tag.put("active_build", buildTag);
    }

    private Town findTown(ServerLevel level) {
        net.minecraft.core.BlockPos anchor = npc.getTownAnchorPos();
        if (anchor != null) {
            return LevelTowns.get(level).getTownAt(anchor)
                .filter(t -> npc.getUUID().equals(t.getBuilderNpcId()))
                .orElse(null);
        }
        // Fallback for builders loaded from saves predating anchor tracking.
        return LevelTowns.get(level).getAllTowns().stream()
            .filter(t -> npc.getUUID().equals(t.getBuilderNpcId()))
            .findFirst().orElse(null);
    }
}
