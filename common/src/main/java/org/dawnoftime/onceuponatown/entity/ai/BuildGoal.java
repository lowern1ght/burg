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

import java.util.List;

public class BuildGoal {
    private enum Phase { MOVING, PLACING, DONE }

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
    private int movingTicks = 0;
    private static final int MOVE_TIMEOUT_TICKS = 3600; // 3 minutes
    private boolean failed = false;

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
        this.goTo = new GoToPosition(npc, usedConnection.pos(), 0.8, 3.0);
    }

    public boolean isFailed() { return failed; }
    public ConnectionPoint getUsedConnection() { return usedConnection; }

    // Returns true when construction is complete (check isFailed() to distinguish success from timeout).
    public boolean tick() {
        return switch (phase) {
            case MOVING -> {
                if (goTo.tick()) {
                    phase = Phase.PLACING;
                } else if (++movingTicks > MOVE_TIMEOUT_TICKS) {
                    // NPC took over 3 minutes to reach the spot - treat as permanently failed
                    failed = true;
                    yield true;
                }
                yield false;
            }
            case PLACING -> {
                if (!(npc.level() instanceof ServerLevel serverLevel)) yield false;

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
                yield false;
            }
            case DONE -> true;
        };
    }
}
