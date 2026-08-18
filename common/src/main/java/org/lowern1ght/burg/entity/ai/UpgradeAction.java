package org.lowern1ght.burg.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import org.lowern1ght.burg.entity.citizen.Citizens;
import org.lowern1ght.burg.building.schematic.BuildSchematic;
import org.lowern1ght.burg.building.schematic.SchematicBlock;
import org.lowern1ght.burg.building.schematic.SchematicEntity;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.town.BuildingDef;
import org.lowern1ght.burg.town.ConnectionPoint;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.PlacedBuilding;
import org.lowern1ght.burg.town.Town;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// Handles visual building upgrades: computes the diff between two NBT levels and applies
// it block-by-block. Increments the building's upgrade level on completion.
public class UpgradeAction implements BuildAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpgradeAction.class);

    private final PlacedBuilding building;
    private final BuildingDef def;
    private final int fromLevel;
    private final Town town;
    // Blocks deeper than the base template the target NBT level extends underground.
    // Shifts the placement origin down so underground galleries land at the correct Y.
    private final int undergroundDepth;
    // Set when a level NBT will not load. Keeps the upgrade from "completing" with nothing built.
    private boolean failed = false;

    public UpgradeAction(PlacedBuilding building, BuildingDef def, int fromLevel, Town town) {
        this.building = building;
        this.def = def;
        this.fromLevel = fromLevel;
        this.town = town;
        this.undergroundDepth = (fromLevel < def.nbtLevels.size())
            ? def.nbtLevels.get(fromLevel).undergroundDepth() : 0;
        town.addUnderUpgrade(building.worldPos);
    }

    @Override
    public BlockPos getTargetPos() { return building.worldPos; }

    @Override
    public BlockPos getOrigin() { return building.worldPos.offset(0, -undergroundDepth, 0); }

    @Override
    public boolean isInstant() { return false; }

    @Override
    public boolean executeInstant(ServerLevel level, Npc npc) { return false; }

    @Override
    public void onArrived(Npc npc) {
        npc.startReading(40 + npc.getRandom().nextInt(61));
    }

    @Override
    public List<SchematicBlock> prepareBlocks(ServerLevel level, Npc npc) {
        ResourceLocation fromNbt = (fromLevel == 0)
            ? def.nbt
            : (fromLevel - 1 < def.nbtLevels.size() ? def.nbtLevels.get(fromLevel - 1).nbt() : null);
        ResourceLocation toNbt = (fromLevel < def.nbtLevels.size()) ? def.nbtLevels.get(fromLevel).nbt() : null;

        if (fromNbt == null || toNbt == null) return List.of();

        // An unreadable NBT must fail the upgrade, not complete it empty.
        //
        // `computeDiff` warns and hands back two empty lists when a template will not load, and
        // `isFailed` used to be a flat `false` — so the builder walked over, read its plan, placed
        // nothing, and `onComplete` bumped the level anyway. The building was then recorded one
        // rung higher than the geometry it actually had, and the rung was gone for good: nothing
        // ever re-runs a level that is already marked done. `settlement_lvl1` is corrupt in this
        // repo, and it is `settlement.json`'s ONLY upgrade — so the starter's one upgrade
        // silently did nothing, and the town said it had done it.
        if (level.getStructureManager().get(toNbt).isEmpty()) {
            LOGGER.error("[OUAT-UPGRADE] REFUSED -- building='{}' level {}->{} target NBT cannot be"
                + " read: '{}'. The level is NOT advanced; fix or restore that file.",
                def.id, fromLevel, fromLevel + 1, toNbt);
            failed = true;
            return List.of();
        }
        if (level.getStructureManager().get(fromNbt).isEmpty()) {
            LOGGER.error("[OUAT-UPGRADE] REFUSED -- building='{}' level {}->{} SOURCE NBT cannot be"
                + " read: '{}'. Diffing against nothing would rebuild the whole level and could"
                + " dismantle what is standing, so nothing is touched.",
                def.id, fromLevel, fromLevel + 1, fromNbt);
            failed = true;
            return List.of();
        }

        BuildSchematic.DiffResult diff = BuildSchematic.computeDiff(level, fromNbt, toNbt, building.rotation, undergroundDepth);

        List<SchematicBlock> steps = new ArrayList<>(diff.toRemove().size() + diff.toAdd().size());
        // Removals first so space is clear before adding new blocks.
        for (BlockPos removePos : diff.toRemove()) {
            steps.add(new SchematicBlock(removePos, Blocks.AIR.defaultBlockState(), null));
        }
        steps.addAll(diff.toAdd());
        return steps;
    }

    @Override
    public void onComplete(ServerLevel level, Npc npc) {
        town.removeUnderUpgrade(building.worldPos);
        // A refused upgrade must not advance the level; the marker above is still cleared so the
        // building does not stay locked under an upgrade that will never finish.
        if (failed) {
            npc.freeHands();
            return;
        }
        int newLevel = fromLevel + 1;

        if (building.getUpgradeLevel() != fromLevel) {
            LOGGER.warn("[OUAT-UPGRADE] Level mismatch on complete -- building='{}' expected={} actual={}",
                def.id, fromLevel, building.getUpgradeLevel());
        } else {
            building.setUpgradeLevel(newLevel);
        }

        if (newLevel <= def.nbtLevels.size()) {
            BuildingDef.NbtLevel newNbtLevel = def.nbtLevels.get(newLevel - 1);
            BlockPos jigsawOrigin = building.worldPos.offset(0, -newNbtLevel.undergroundDepth(), 0);
            List<ConnectionPoint> newPoints = BuildSchematic.readJigsawPointsFromNbt(
                level, jigsawOrigin, newNbtLevel.nbt(), building.rotation);
            List<ConnectionPoint> existing = town.getAvailableConnectionPoints();
            for (ConnectionPoint cp : newPoints) {
                if (existing.stream().noneMatch(e -> e.pos().equals(cp.pos()))) {
                    town.addFreeConnection(cp);
                }
            }

            ResourceLocation fromNbt = (fromLevel == 0)
                ? def.nbt
                : (fromLevel - 1 < def.nbtLevels.size() ? def.nbtLevels.get(fromLevel - 1).nbt() : null);
            if (fromNbt != null) {
                List<SchematicEntity> toSpawn = BuildSchematic.computeEntityDiff(
                    level, fromNbt, newNbtLevel.nbt(), building.rotation, building.worldPos, undergroundDepth);
                for (SchematicEntity se : toSpawn) {
                    EntityType.by(se.nbt()).ifPresent(type -> {
                        Entity entity = type.create(level);
                        if (entity != null) {
                            entity.load(se.nbt());
                            entity.moveTo(se.worldPos().x, se.worldPos().y, se.worldPos().z,
                                          entity.getYRot(), entity.getXRot());
                            // Same as NewBuildAction: a villager arriving with one of our
                            // buildings belongs to the town that built it.
                            if (entity instanceof Villager villager && npc.getTownAnchorPos() != null) {
                                Citizens.enlist(villager, npc.getTownAnchorPos());
                            }
                            level.addFreshEntity(entity);
                        }
                    });
                }
            }
        }

        LevelTowns.get(level).markDirty();
        npc.freeHands();
    }

    @Override
    public boolean isFailed() { return failed; }

    // Upgrades do not persist mid-progress state; the queue entry remains and the NPC
    // will redo the whole upgrade after a server restart.
    @Override
    public void saveTo(CompoundTag tag) {}
}
