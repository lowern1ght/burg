package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.schematic.JigsawConnector;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.town.ActiveBuildState;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.QueueEntry;
import org.dawnoftime.onceuponatown.town.Town;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SimpleStateMachine {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleStateMachine.class);

    public enum State { IDLE, BUILD }

    private int maxFailCount()  { return BuilderConfigDataHandler.get().maxPlacementRetries; }
    private int idleWaitTicks() { return BuilderConfigDataHandler.get().idleWaitTicks; }
    private int openPhaseTicks(){ return BuilderConfigDataHandler.get().autonomousRoadIntervalTicks; }

    private final Npc npc;
    private State current = State.IDLE;
    private int idleTimer = 0;
    private BuildTask activeBuild = null;
    private int buildStateTicks = 0;
    // The player-queued entry currently being built, or null if not building from queue.
    private QueueEntry activeQueueEntry = null;
    // Number of consecutive idle ticks where the current queue cursor entry could not be placed.
    private int playerQueueFailCount = 0;
    // Counts ticks in the open phase (no player queue) toward the configured autonomous road interval.
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
        // On each idle tick, check if Town has a saved build state for this builder's slot.
        // Runs before the timer so the NPC resumes immediately on first tick after restart.
        if (npc.level() instanceof ServerLevel resumeLevel) {
            Town resumeTown = findTown(resumeLevel);
            if (resumeTown != null) {
                int mySlot = resumeTown.getBuilderSlot(npc.getUUID());
                ActiveBuildState saved = resumeTown.getActiveBuild(mySlot);
                if (saved != null) {
                    BuildGoal resumed = BuildGoal.fromActiveBuildState(saved, npc, resumeTown, resumeLevel);
                    if (resumed != null) {
                        activeBuild = resumed;
                        activeQueueEntry = saved.queueDefId() != null
                            ? new QueueEntry.NewBuild(saved.queueDefId()) : null;
                        current = State.BUILD;
                        if (activeQueueEntry != null) {
                            int idx = resumeTown.getConstructionQueue().indexOf(activeQueueEntry);
                            if (idx >= 0) resumeTown.claimQueueEntry(idx, npc.getUUID());
                        }
                        return;
                    } else {
                        // Invalid saved state (e.g. def removed); discard to avoid looping.
                        resumeTown.clearActiveBuild(mySlot);
                        LevelTowns.get(resumeLevel).markDirty();
                    }
                }
            }
        }

        if (++idleTimer < idleWaitTicks()) return;
        idleTimer = 0;
        if (!(npc.level() instanceof ServerLevel serverLevel)) return;

        Town town = findTown(serverLevel);
        if (town == null) {
            if (idleTimer == 1)
                LOGGER.warn("[OUAT-NPC] {} cannot find its town. AnchorPos: {}",
                    npc.getUUID(), npc.getTownAnchorPos());
            return;
        }

        List<ConnectionPoint> freePoints = town.getAvailableConnectionPoints();
        if (freePoints.isEmpty()) {
            return;
        }

        List<BoundingBox> occupied = town.getOccupiedBoxes();
        List<ConnectionPoint> shuffledPoints = new ArrayList<>(freePoints);

        // Only the primary builder (slot 0) expands streets autonomously.
        boolean isPrimaryBuilder = town.getBuilderSlot(npc.getUUID()) == 0;

        if (!town.getConstructionQueue().isEmpty()) {
            openPhaseTimer = 0;
            tickPlayerQueue(serverLevel, town, shuffledPoints, occupied);
            // Primary builder also expands streets when the queue entry could not be placed this tick.
            if (current == State.IDLE && isPrimaryBuilder) {
                tickStreetsOnly(serverLevel, town, shuffledPoints, occupied);
            }
        } else {
            // No player instructions: primary builder extends roads once per minute.
            if (isPrimaryBuilder && ++openPhaseTimer >= openPhaseTicks()) {
                openPhaseTimer = 0;
                tickStreetsOnly(serverLevel, town, shuffledPoints, occupied);
            }
        }
    }

    // Player-directed queue: scans the full queue from index 0, skipping entries claimed by other builders.
    // Registers a claim before starting a build and releases it on completion or failure.
    private void tickPlayerQueue(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<QueueEntry> queue = town.getConstructionQueue();
        if (queue.isEmpty()) return;

        UUID myId = npc.getUUID();

        for (int i = 0; i < queue.size(); i++) {
            // Skip entries claimed by another builder.
            if (town.isQueueEntryClaimedByOther(i, myId)) continue;

            QueueEntry entry = queue.get(i);

            // Upgrade entries: walk to the building and run the visual diff.
            if (entry instanceof QueueEntry.Upgrade upgradeEntry) {
                PlacedBuilding building = town.getBuildings().stream()
                    .filter(b -> b.worldPos.equals(upgradeEntry.buildingWorldPos()))
                    .findFirst().orElse(null);
                BuildingDef def = BuildingDataHandler.get(upgradeEntry.defId()).orElse(null);

                if (building == null || def == null) {
                    town.releaseQueueClaim(i, myId);
                    town.consumeQueueEntry(entry);
                    LevelTowns.get(serverLevel).markDirty();
                    return;
                }

                town.claimQueueEntry(i, myId);
                activeBuild = new BuildGoal(npc, new UpgradeAction(building, def, upgradeEntry.fromLevel(), town));
                activeQueueEntry = entry;
                playerQueueFailCount = 0;
                current = State.BUILD;
                broadcastUpgradeStart(serverLevel, upgradeEntry.defId(), upgradeEntry.buildingWorldPos());
                NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                return;
            }

            // New build entries: find a compatible connection point.
            String defId = ((QueueEntry.NewBuild) entry).defId();
            BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
            if (def == null) {
                town.releaseQueueClaim(i, myId);
                town.consumeQueueEntry(entry);
                LevelTowns.get(serverLevel).markDirty();
                return;
            }

            // Prerequisites not met: skip this entry and try the next one.
            if (!town.meetsPrerequisites(def)) {
                LOGGER.debug("[OUAT-QUEUE] Prerequisites not met for '{}' at index {} -- skipping.", defId, i);
                continue;
            }

            int poolMatches = 0;
            int bbOverlaps = 0;
            int noConnector = 0;

            for (ConnectionPoint point : shuffledPoints) {
                if (!point.targetName().isEmpty() && !def.entryPool.equals(point.targetName())) continue;
                poolMatches++;
                PlacementOutcome outcome = attemptPlacement(serverLevel, point, occupied, def);
                if (outcome.succeeded()) {
                    PlacementSuccess s = outcome.success();
                    town.claimQueueEntry(i, myId);
                    town.useConnection(point);
                    LevelTowns.get(serverLevel).markDirty();
                    activeBuild = new BuildGoal(npc, new NewBuildAction(
                        def, point, s.pos(), s.rotation(), s.entryConnectorWorldPos(), List.of(), town));
                    if (s.bb() != null) town.addUnderConstruction(def.id, s.pos(), s.bb(), s.rotation());
                    activeQueueEntry = entry;
                    playerQueueFailCount = 0;
                    int mySlotQ = town.getBuilderSlot(myId);
                    if (mySlotQ >= 0) {
                        town.setActiveBuild(mySlotQ, new ActiveBuildState(
                            def.id, s.pos(), s.rotation(), point.pos(), point.direction(),
                            point.targetName(), s.entryConnectorWorldPos(), List.of(), defId));
                        LevelTowns.get(serverLevel).markDirty();
                    }
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, defId, s.pos());
                    NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return;
                }
                if (outcome.failure() == FailReason.BOUNDING_BOX_OVERLAP)    bbOverlaps++;
                else if (outcome.failure() == FailReason.NO_COMPATIBLE_CONNECTOR) noConnector++;
            }

            // Could not place this entry this tick.
            if (poolMatches == 0) {
                // No connection point accepts this pool -- skip to the next entry.
                continue;
            }

            playerQueueFailCount++;
            if (playerQueueFailCount >= maxFailCount()) {
                LOGGER.warn("[OUAT-QUEUE] Placement failed for '{}' at index {} after {} attempts -- "
                    + "totalCPs={} poolMatches={} bbOverlap={} noConnector={} -- skipping entry.",
                    defId, i, maxFailCount(),
                    shuffledPoints.size(), poolMatches, bbOverlaps, noConnector);
                playerQueueFailCount = 0;
                town.releaseQueueClaim(i, myId);
                // Advance the legacy cursor so existing save/restore paths stay valid.
                town.advanceQueueCursor();
                LevelTowns.get(serverLevel).markDirty();
            }
            return;
        }
    }

    // Post-bootstrap idle phase: the builder only places streets autonomously.
    // Non-street connection points are preserved for the player construction queue.
    // Street points follow the same maxFailCount() kill logic as before.
    private void tickStreetsOnly(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> shuffledPoints, List<BoundingBox> occupied) {
        List<BuildingDef> streetCandidates = new ArrayList<>(town.getBuildableBuildings().stream()
            .filter(d -> Constants.STREETS_POOL.equals(d.entryPool))
            .toList());

        List<ConnectionPoint> deadPoints = new ArrayList<>();
        List<ConnectionPoint> toIncrementFail = new ArrayList<>();

        for (ConnectionPoint point : shuffledPoints) {
            if (!Constants.STREETS_POOL.equals(point.targetName())) continue;

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
                    int mySlotS = town.getBuilderSlot(npc.getUUID());
                    if (mySlotS >= 0) {
                        town.setActiveBuild(mySlotS, new ActiveBuildState(
                            candidate.id, s.pos(), s.rotation(), point.pos(), point.direction(),
                            point.targetName(), s.entryConnectorWorldPos(), candidate.constructionCost, null));
                        LevelTowns.get(serverLevel).markDirty();
                    }
                    current = State.BUILD;
                    broadcastBuildStart(serverLevel, candidate.id, s.pos());
                    NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return;
                }
                physicalFailure = true;
            }

            if (physicalFailure) {
                int nextFail = point.failCount() + 1;
                if (nextFail >= maxFailCount()) {
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
            if (npc.level() instanceof ServerLevel sl) {
                Town qTown = findTown(sl);
                // Clear the persisted build state regardless of success or failure.
                if (qTown != null) {
                    int mySlot = qTown.getBuilderSlot(npc.getUUID());
                    if (mySlot >= 0) qTown.clearActiveBuild(mySlot);
                }
                if (!activeBuild.isFailed() && activeQueueEntry != null && qTown != null) {
                    // Release the claim before consuming so indices stay consistent.
                    int claimIdx = qTown.getConstructionQueue().indexOf(activeQueueEntry);
                    if (claimIdx >= 0) qTown.releaseQueueClaim(claimIdx, npc.getUUID());
                    String placedDefId = activeQueueEntry instanceof QueueEntry.NewBuild nb ? nb.defId() : null;
                    qTown.consumeQueueEntry(activeQueueEntry);
                    if (placedDefId != null) qTown.onBuildingPlaced(placedDefId);
                    LevelTowns.get(sl).markDirty();
                } else if (activeBuild.isFailed() && activeQueueEntry != null && qTown != null) {
                    // Release claim on failure so another builder can attempt this entry.
                    int claimIdx = qTown.getConstructionQueue().indexOf(activeQueueEntry);
                    if (claimIdx >= 0) qTown.releaseQueueClaim(claimIdx, npc.getUUID());
                }
            }
            // Remove construction/upgrade markers and fire targeted packets.
            if (npc.level() instanceof ServerLevel sl) {
                Town doneTown = findTown(sl);
                if (doneTown != null) {
                    doneTown.removeUnderConstruction(completedPos);
                    doneTown.removeUnderUpgrade(completedPos);
                    BlockPos anchor = npc.getTownAnchorPos();
                    NetworkHelper.pushBuildingListToWatchers(sl, doneTown, anchor);
                    NetworkHelper.pushStockToWatchers(sl, doneTown, anchor);
                    // If a house was just built, total resident count changed.
                    if (activeQueueEntry instanceof QueueEntry.NewBuild nb) {
                        org.dawnoftime.onceuponatown.datapack.BuildingDataHandler.get(nb.defId()).ifPresent(def -> {
                            if (def.residents > 0) {
                                NetworkHelper.pushCitizenUpdateToWatchers(sl, doneTown, anchor);
                            }
                        });
                    }
                }
            }
            activeQueueEntry = null;
            activeBuild = null;
            buildStateTicks = 0;
            current = State.IDLE;
        }
    }

    private void broadcastBuildStart(ServerLevel level, String buildingId, BlockPos pos) {
        Component msg = Component.literal("[Town] ")
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
        Component msg = Component.literal("[Town] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("Upgrading: ")
                .withStyle(ChatFormatting.WHITE))
            .append(Component.literal(buildingId)
                .withStyle(ChatFormatting.YELLOW))
            .append(Component.literal(" @ " + pos.toShortString())
                .withStyle(ChatFormatting.GRAY));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private Town findTown(ServerLevel level) {
        net.minecraft.core.BlockPos anchor = npc.getTownAnchorPos();
        if (anchor != null) {
            return LevelTowns.get(level).getTownAt(anchor)
                .filter(t -> t.getBuilderNpcIds().contains(npc.getUUID()))
                .orElse(null);
        }
        // Fallback for builders loaded from saves predating anchor tracking.
        return LevelTowns.get(level).getAllTowns().stream()
            .filter(t -> t.getBuilderNpcIds().contains(npc.getUUID()))
            .findFirst().orElse(null);
    }
}
