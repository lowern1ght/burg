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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SimpleStateMachine {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(SimpleStateMachine.class);

    public enum State { IDLE, BUILD }

    private int idleWaitTicks() { return BuilderConfigDataHandler.get().idleWaitTicks; }
    private int openPhaseTicks(){ return BuilderConfigDataHandler.get().autonomousRoadIntervalTicks; }

    private final Npc npc;
    private State current = State.IDLE;
    private int idleTimer = 0;
    private BuildTask activeBuild = null;
    private int buildStateTicks = 0;
    // The player-queued entry currently being built, or null if not building from queue.
    private QueueEntry activeQueueEntry = null;
    // Counts ticks in the open phase (no player queue) toward the configured autonomous road interval.
    private int openPhaseTimer = 0;
    // Round-robin index for street CP selection (runtime-only, resets to 0 on server load).
    private int streetCpCursor = 0;
    // DefIds that have already received a suppressed skip message this session.
    // Cleared when the defId is placed or falls out of the queue.
    private final Set<String> warnedDefIds = new HashSet<>();

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
            if (isPrimaryBuilder && (openPhaseTimer += idleWaitTicks()) >= openPhaseTicks()) {
                openPhaseTimer = 0;
                tickStreetsOnly(serverLevel, town, shuffledPoints, occupied);
            }
        }
    }

    // Player-directed queue: scans all entries each cycle, skipping claimed or unplaceable entries.
    // CPs are never removed on failure -- only a successful placement consumes a CP.
    private void tickPlayerQueue(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> freePoints, List<BoundingBox> occupied) {
        List<QueueEntry> queue = town.getConstructionQueue();
        if (queue.isEmpty()) return;

        UUID myId = npc.getUUID();
        String townName = town.getName();

        // Clear warnedDefIds for any defId no longer present in the queue.
        Set<String> activeDefIds = new HashSet<>();
        for (QueueEntry e : queue) {
            if (e instanceof QueueEntry.NewBuild nb) activeDefIds.add(nb.defId());
        }
        warnedDefIds.retainAll(activeDefIds);

        for (int i = 0; i < queue.size(); i++) {
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
                current = State.BUILD;
                broadcastUpgradeStart(serverLevel, upgradeEntry.defId(), upgradeEntry.buildingWorldPos());
                NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                return;
            }

            String defId = ((QueueEntry.NewBuild) entry).defId();
            BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
            if (def == null) {
                town.releaseQueueClaim(i, myId);
                town.consumeQueueEntry(entry);
                LevelTowns.get(serverLevel).markDirty();
                continue;
            }

            if (!town.meetsPrerequisites(def)) {
                warnedDefIds.add(defId);
                continue;
            }

            // Collect all CPs matching this building's pool.
            List<ConnectionPoint> matchingCps = new ArrayList<>();
            for (ConnectionPoint cp : freePoints) {
                if (!cp.targetName().isEmpty() && def.entryPool.equals(cp.targetName())) {
                    matchingCps.add(cp);
                }
            }

            if (matchingCps.isEmpty()) {
                warnedDefIds.add(defId);
                continue;
            }

            shuffleInPlace(matchingCps);

            int bbOverlaps = 0;
            int noConnector = 0;

            for (ConnectionPoint point : matchingCps) {
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
                    int mySlotQ = town.getBuilderSlot(myId);
                    if (mySlotQ >= 0) {
                        town.setActiveBuild(mySlotQ, new ActiveBuildState(
                            def.id, s.pos(), s.rotation(), point.pos(), point.direction(),
                            point.targetName(), s.entryConnectorWorldPos(), List.of(), defId));
                        LevelTowns.get(serverLevel).markDirty();
                    }
                    current = State.BUILD;
                    warnedDefIds.remove(defId);
                    broadcastBuildStart(serverLevel, defId, s.pos());
                    NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                    return;
                }
                if (outcome.failure() == FailReason.BOUNDING_BOX_OVERLAP)        bbOverlaps++;
                else if (outcome.failure() == FailReason.NO_COMPATIBLE_CONNECTOR) noConnector++;
            }

            if (noConnector > 0 && bbOverlaps == 0) {
                warnedDefIds.add(defId);
            }
        }
    }

    // Post-bootstrap idle phase: the primary builder places one street per cycle via round-robin CP selection.
    // Non-street connection points are preserved for the player construction queue.
    // CPs are never removed on failure.
    private void tickStreetsOnly(ServerLevel serverLevel, Town town,
                                  List<ConnectionPoint> freePoints, List<BoundingBox> occupied) {
        List<BuildingDef> streetCandidates = new ArrayList<>(town.getBuildableBuildings().stream()
            .filter(d -> Constants.STREETS_POOL.equals(d.entryPool))
            .toList());

        // Collect street CPs sorted by position for deterministic round-robin.
        List<ConnectionPoint> streetCps = new ArrayList<>();
        for (ConnectionPoint cp : freePoints) {
            if (Constants.STREETS_POOL.equals(cp.targetName())) streetCps.add(cp);
        }
        streetCps.sort(java.util.Comparator.comparingLong(cp -> cp.pos().asLong()));

        if (streetCps.isEmpty() || streetCandidates.isEmpty()) {
            broadcastVillageFullIfNeeded(serverLevel, town);
            return;
        }

        ConnectionPoint chosen = streetCps.get(streetCpCursor % streetCps.size());
        streetCpCursor++;

        shuffleInPlace(streetCandidates);

        for (BuildingDef candidate : streetCandidates) {
            PlacementOutcome outcome = attemptPlacement(serverLevel, chosen, occupied, candidate);
            if (outcome.succeeded()) {
                PlacementSuccess s = outcome.success();
                town.useConnection(chosen);
                LevelTowns.get(serverLevel).markDirty();
                activeBuild = new BuildGoal(npc, new NewBuildAction(
                    candidate, chosen, s.pos(), s.rotation(), s.entryConnectorWorldPos(),
                    candidate.constructionCost, town));
                if (s.bb() != null) town.addUnderConstruction(candidate.id, s.pos(), s.bb(), s.rotation());
                int mySlotS = town.getBuilderSlot(npc.getUUID());
                if (mySlotS >= 0) {
                    town.setActiveBuild(mySlotS, new ActiveBuildState(
                        candidate.id, s.pos(), s.rotation(), chosen.pos(), chosen.direction(),
                        chosen.targetName(), s.entryConnectorWorldPos(), candidate.constructionCost, null));
                    LevelTowns.get(serverLevel).markDirty();
                }
                current = State.BUILD;
                broadcastBuildStart(serverLevel, candidate.id, s.pos());
                NetworkHelper.pushBuildingListToWatchers(serverLevel, town, npc.getTownAnchorPos());
                return;
            }
        }

        broadcastVillageFullIfNeeded(serverLevel, town);
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

        Optional<BoundingBox> maybeBb = def.terrainMatching
            ? BuildSchematic.computeFootprintBoundingBox(serverLevel, finalPos, def.nbt, rotation)
            : BuildSchematic.computeBoundingBox(serverLevel, finalPos, def.nbt, rotation);
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

    private void broadcastSkipMessage(ServerLevel level, String townName, String message) {
        Component msg = Component.literal("[" + townName + "] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
    }

    private void broadcastVillageFullIfNeeded(ServerLevel level, Town town) {
        if (!town.checkVillageFullTransition()) return;
        Component msg = Component.literal("[" + town.getName() + "] ")
            .withStyle(ChatFormatting.GOLD)
            .append(Component.literal("has no connection points left. The village cannot expand further.")
                .withStyle(ChatFormatting.WHITE));
        level.getServer().getPlayerList().broadcastSystemMessage(msg, false);
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
