package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.terrain.BeardThinMimic;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BuildGoal {
    private enum Phase { MOVING, PLACING, DONE }

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildGoal.class);

    private final Npc npc;
    private final BuildingDef def;
    private final ConnectionPoint usedConnection;
    private final BlockPos finalPlacementPos;
    // Rotation precomputed by SimpleStateMachine from connector alignment (not hardcoded SOUTH assumption).
    private final Rotation rotation;
    // World position of the entry connector in the newly placed template.
    // Passed to readJigsawPoints so it is excluded from the list of new free connections.
    private final BlockPos entryConnectorWorldPos;
    // Construction cost stored here so resources are only deducted on successful placement, not upfront.
    private final List<ItemCost> constructionCost;
    private final Town town;
    private Phase phase = Phase.MOVING;
    private final GoToPosition goTo;
    private boolean failed = false;
    // Tick counters for stuck-detection logs.
    private int movingTicks = 0;
    private int placingTicks = 0;

    public BuildGoal(Npc npc, BuildingDef def, ConnectionPoint usedConnection,
                     BlockPos finalPlacementPos, Rotation rotation, BlockPos entryConnectorWorldPos,
                     List<ItemCost> constructionCost, Town town) {
        this.npc = npc;
        this.def = def;
        this.usedConnection = usedConnection;
        this.finalPlacementPos = finalPlacementPos;
        this.rotation = rotation;
        this.entryConnectorWorldPos = entryConnectorWorldPos;
        this.constructionCost = constructionCost;
        this.town = town;
        // arrivalRadius raised from 3.0 to 5.5: the vanilla pathfinder stops naturally at ~4-5 blocks
        // from its target, so 3.0 caused an infinite re-issue loop where the NPC was right next to
        // the connection point but never triggered the PLACING phase.
        this.goTo = new GoToPosition(npc, usedConnection.pos(), 0.8, 5.5);
    }

    public boolean isFailed() { return failed; }
    public ConnectionPoint getUsedConnection() { return usedConnection; }
    public String getDefId() { return def.id; }
    public BlockPos getFinalPlacementPos() { return finalPlacementPos; }

    // Returns true when construction is complete (success or failure).
    public boolean tick() {
        return switch (phase) {
            case MOVING -> {
                movingTicks++;
                // Log every 200t while stuck navigating so we can detect infinite MOVING states.
                if (movingTicks % 200 == 0) {
                    LOGGER.warn("[OUAT-BUILD] NPC {} STUCK IN MOVING phase -- building='{}' target={} elapsedMovingTicks={}",
                        npc.getUUID(), def.id, usedConnection.pos(), movingTicks);
                }
                if (goTo.tick()) {
                    LOGGER.debug("[OUAT-BUILD] NPC {} reached connection pos={} after {}t, starting placement of '{}'",
                        npc.getUUID(), usedConnection.pos(), movingTicks, def.id);
                    phase = Phase.PLACING;
                }
                yield false;
            }
            case PLACING -> {
                placingTicks++;
                if (!(npc.level() instanceof ServerLevel serverLevel)) {
                    // Not a server level -- log once per 20t to avoid spam.
                    if (placingTicks % 20 == 0) {
                        LOGGER.warn("[OUAT-BUILD] NPC {} PLACING: level is not ServerLevel (tick={}), building='{}'",
                            npc.getUUID(), placingTicks, def.id);
                    }
                    yield false;
                }

                LOGGER.debug("[OUAT-BUILD] NPC {} attempting placement -- building='{}' pos={} rotation={} terrainMatching={} placingTick={}",
                    npc.getUUID(), def.id, finalPlacementPos, rotation, def.terrainMatching, placingTicks);

                // Step 1: carve terrain above layer 0 before placement so carve only touches
                // pre-existing terrain, never the building's own blocks.
                // Paths use placeTerrainMatched and must not go through BeardThinMimic.
                if (!def.terrainMatching) {
                    serverLevel.getStructureManager().get(def.nbt).ifPresent(template ->
                        BeardThinMimic.prePlace(serverLevel, finalPlacementPos, template, rotation)
                    );
                }

                // Step 2: place the structure.
                // BuildSchematic.place() skips AIR blocks from the NBT so existing terrain is not excavated.
                boolean placed = def.terrainMatching
                    ? BuildSchematic.placeTerrainMatched(serverLevel, finalPlacementPos, def.nbt, rotation)
                    : BuildSchematic.place(serverLevel, finalPlacementPos, def.nbt, rotation);

                if (placed) {
                    LOGGER.info("[OUAT-BUILD] Placement SUCCESS -- building='{}' pos={} rotation={} movingTicks={} placingTicks={}",
                        def.id, finalPlacementPos, rotation, movingTicks, placingTicks);
                    // Deduct resources only after successful placement - not upfront.
                    // This prevents resource loss when the NPC fails to reach the target.
                    town.getTownInventory().removeStock(constructionCost);
                    BuildSchematic.replaceJigsawInWorld(serverLevel, usedConnection.pos());
                    npc.onBuildComplete(finalPlacementPos, def.id, usedConnection, rotation, entryConnectorWorldPos);

                    // Step 3: fill gaps below the building and apply perimeter skirt after placement.
                    if (!def.terrainMatching) {
                        serverLevel.getStructureManager().get(def.nbt).ifPresent(template ->
                            BeardThinMimic.postPlace(serverLevel, finalPlacementPos, template, rotation)
                        );
                    } else {
                        // Pond-edge pass: replace DIRT_PATH blocks bordering water with OAK_PLANKS.
                        BuildSchematic.computeBoundingBox(serverLevel, finalPlacementPos, def.nbt, rotation)
                            .ifPresent(bb -> BuildSchematic.applyPondEdgeRules(serverLevel, bb));
                    }

                    phase = Phase.DONE;
                    yield true;
                }

                // placement returned false -- this should only happen if the NBT is missing.
                LOGGER.error("[OUAT-BUILD] Placement FAILED (place() returned false) -- building='{}' nbt='{}' pos={} rotation={} placingTick={}",
                    def.id, def.nbt, finalPlacementPos, rotation, placingTicks);
                yield false;
            }
            case DONE -> true;
        };
    }
}
