package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.dawnoftime.onceuponatown.building.schematic.BuildSchematic;
import org.dawnoftime.onceuponatown.building.schematic.SchematicBlock;
import org.dawnoftime.onceuponatown.building.schematic.SchematicEntity;
import org.dawnoftime.onceuponatown.building.schematic.SchematicReader;
import org.dawnoftime.onceuponatown.building.terrain.TerrainCarver;
import org.dawnoftime.onceuponatown.datapack.BuilderConfigDataHandler;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ConnectionPoint;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Handles new building construction: terrain prep, full NBT block list, resource deduction,
// town registration, and entity spawning on completion.
public class NewBuildAction implements BuildAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(NewBuildAction.class);

    final BuildingDef def;
    private final ConnectionPoint usedConnection;
    private final BlockPos finalPlacementPos;
    private final Rotation rotation;
    private final BlockPos entryConnectorWorldPos;
    private final List<ItemCost> constructionCost;
    private final Town town;
    private boolean failed = false;
    // Set true for server-restart resumes to skip terrain re-carving and the reading animation.
    private boolean skipTerrainPrep = false;
    private boolean skipInitialReading = false;
    // Stored during prepareBlocks; needed for entity spawning in onComplete.
    private StructureTemplate cachedTemplate = null;

    public NewBuildAction(BuildingDef def, ConnectionPoint usedConnection,
                          BlockPos finalPlacementPos, Rotation rotation,
                          BlockPos entryConnectorWorldPos, List<ItemCost> constructionCost,
                          Town town) {
        this.def = def;
        this.usedConnection = usedConnection;
        this.finalPlacementPos = finalPlacementPos;
        this.rotation = rotation;
        this.entryConnectorWorldPos = entryConnectorWorldPos;
        this.constructionCost = constructionCost;
        this.town = town;
    }

    @Override
    public BlockPos getTargetPos() { return usedConnection.pos(); }

    @Override
    public BlockPos getOrigin() { return finalPlacementPos; }

    @Override
    public boolean isInstant() { return def.terrainMatching; }

    @Override
    public boolean executeInstant(ServerLevel level, Npc npc) {
        boolean placed = BuildSchematic.placeTerrainMatched(level, finalPlacementPos, def.nbt, rotation);
        if (placed) {
            BuildSchematic.computeBoundingBox(level, finalPlacementPos, def.nbt, rotation)
                .ifPresent(bb -> BuildSchematic.applyPondEdgeRules(level, bb));
        }
        return placed;
    }

    @Override
    public void onArrived(Npc npc) {
        if (!def.terrainMatching && !skipInitialReading) {
            BuilderConfigDataHandler.Config cfg = BuilderConfigDataHandler.get();
            npc.startReading(cfg.planReadMinTicks + npc.getRandom().nextInt(cfg.planReadMaxTicks - cfg.planReadMinTicks + 1));
        }
    }

    @Override
    public List<SchematicBlock> prepareBlocks(ServerLevel level, Npc npc) {
        Optional<StructureTemplate> templateOpt = level.getStructureManager().get(def.nbt);
        if (templateOpt.isEmpty()) {
            LOGGER.error("[OUAT-BUILD] NBT template not found -- building='{}' nbt='{}'", def.id, def.nbt);
            failed = true;
            return List.of();
        }
        cachedTemplate = templateOpt.get();

        if (!skipTerrainPrep) {
            TerrainCarver.prePlace(level, finalPlacementPos, cachedTemplate, rotation);
            TerrainCarver.postPlace(level, finalPlacementPos, cachedTemplate, rotation);
        }

        List<SchematicBlock> blocks = SchematicReader.readSortedBlocks(cachedTemplate, rotation);
        if (blocks.isEmpty()) {
            // Fallback: instant placement if the reader returned nothing.
            LOGGER.warn("[OUAT-BUILD] Empty block list from reader -- building='{}', using instant fallback", def.id);
            BuildSchematic.place(level, finalPlacementPos, def.nbt, rotation);
            failed = true; // signal BuildGoal to skip normal completion
        }
        return blocks;
    }

    @Override
    public void onComplete(ServerLevel level, Npc npc) {
        town.getTownInventory().removeStock(constructionCost);
        BuildSchematic.replaceJigsawInWorld(level, usedConnection.pos());
        npc.onBuildComplete(finalPlacementPos, def.id, usedConnection, rotation, entryConnectorWorldPos);

        if (cachedTemplate != null) {
            List<SchematicEntity> entities = SchematicReader.readEntities(cachedTemplate, rotation, finalPlacementPos);
            for (SchematicEntity se : entities) {
                EntityType.by(se.nbt()).ifPresent(type -> {
                    Entity entity = type.create(level);
                    if (entity != null) {
                        entity.load(se.nbt());
                        entity.moveTo(se.worldPos().x, se.worldPos().y, se.worldPos().z,
                                      entity.getYRot(), entity.getXRot());
                        level.addFreshEntity(entity);
                    }
                });
            }
        }

        npc.freeHands();
    }

    @Override
    public boolean isFailed() { return failed; }

    @Override
    public void saveTo(CompoundTag tag) {
        tag.putString("def_id", def.id);
        tag.putLong("placement_pos", finalPlacementPos.asLong());
        tag.putString("rotation", rotation.name());
        tag.putLong("connection_pos", usedConnection.pos().asLong());
        tag.putString("connection_dir", usedConnection.direction().getName());
        tag.putString("connection_target", usedConnection.targetName());
        tag.putLong("entry_connector_pos", entryConnectorWorldPos.asLong());

        ListTag costList = new ListTag();
        for (ItemCost cost : constructionCost) {
            CompoundTag costTag = new CompoundTag();
            costTag.putString("item", BuiltInRegistries.ITEM.getKey(cost.item()).toString());
            costTag.putInt("amount", cost.amount());
            costList.add(costTag);
        }
        tag.put("cost", costList);
    }

    // Called after a resume to register the in-progress build on the town map.
    void registerAsUnderConstruction(Town town, ServerLevel level) {
        BuildSchematic.computeBoundingBox(level, finalPlacementPos, def.nbt, rotation)
            .ifPresent(bb -> town.addUnderConstruction(def.id, finalPlacementPos, bb, rotation));
    }

    // Reconstructs a NewBuildAction from saved NBT. Returns null if the data is invalid.
    static NewBuildAction resumeFrom(CompoundTag tag, Npc npc, Town town) {
        String defId = tag.getString("def_id");
        BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
        if (def == null) return null;

        Direction dir = Direction.byName(tag.getString("connection_dir"));
        if (dir == null) return null;

        BlockPos finalPos = BlockPos.of(tag.getLong("placement_pos"));
        Rotation rot;
        try {
            rot = Rotation.valueOf(tag.getString("rotation"));
        } catch (IllegalArgumentException e) {
            rot = Rotation.NONE;
        }

        ConnectionPoint conn = ConnectionPoint.of(
            BlockPos.of(tag.getLong("connection_pos")),
            dir,
            tag.getString("connection_target")
        );
        BlockPos entryPos = BlockPos.of(tag.getLong("entry_connector_pos"));

        ListTag costList = tag.getList("cost", 10);
        List<ItemCost> cost = new ArrayList<>();
        for (int i = 0; i < costList.size(); i++) {
            CompoundTag c = costList.getCompound(i);
            ResourceLocation rl = ResourceLocation.tryParse(c.getString("item"));
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                cost.add(new ItemCost(item, c.getInt("amount")));
            }
        }

        NewBuildAction action = new NewBuildAction(def, conn, finalPos, rot, entryPos, cost, town);
        action.skipTerrainPrep = true;
        action.skipInitialReading = true;
        return action;
    }
}
