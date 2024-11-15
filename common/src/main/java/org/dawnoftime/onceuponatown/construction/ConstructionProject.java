package org.dawnoftime.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.Building;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/*
      Construction projects :
    - Phase 1 : remove entities (paintings, armor stands...)
    - Phase 2 : remove blocks
    - Phase 3 : add blocks
    - phase 4 : add entities (paintings, armor stands...)

      Depending on the project and the terrain, several phases may be empty
      4 types of projects: New build, upgrade, repair and demolition
     */
public class ConstructionProject {
    protected final Level level;
    protected final String name;
    private final ProjectType projectType;
    protected final BuildVariant buildVariant;
    private final BuildingPlacementSettings placementSettings;
    private final List<ProjectStep> projectSteps;
    private int progress = 0;
    protected boolean completed;

    private ConstructionProject(Level level, String name, ProjectType projectType, BuildVariant variant, BuildingPlacementSettings placementSettings, List<ProjectStep> projectSteps) {
        this.level = level;
        this.name = name;
        this.projectType = projectType;
        this.buildVariant = variant;
        this.placementSettings = placementSettings;
        this.projectSteps = projectSteps;
    }

    public ConstructionProject newBuildProject(Level level, String name, BuildVariant variant, BuildingPlacementSettings placementSettings) {
        return newBuildProject(level, name, variant, 1, placementSettings);
    }

    public ConstructionProject newBuildProject(Level level, String name, BuildVariant variant, int buildingLevel, BuildingPlacementSettings buildingPlacementSettings) {
        return createProject(level, name, ProjectType.NEW_BUILD, variant, buildingLevel, buildingPlacementSettings);
    }

    public ConstructionProject upgradeProject(Level level, String name, Building building) {
        return upgradeProject(level, name, building, building.getLevel() + 1);
    }

    public ConstructionProject upgradeProject(Level level, String name, Building building, int wantedLevel) {
        return createProject(level, name, ProjectType.UPGRADE, building.getVariant(), wantedLevel, building.getPlacementSettings());
    }

    public ConstructionProject repairProject(Level level, String name, Building building) {
        return createProject(level, name, ProjectType.REPAIR, building.getVariant(), building.getLevel(), building.getPlacementSettings());
    }

    private ConstructionProject createProject(Level level, String name, ProjectType projectType, BuildVariant buildVariant, int buildingLevel, BuildingPlacementSettings buildingPlacementSettings) {

        // 1. Create ConstructionPlan
        // 2. Scan plot. List blocks in variable existingBlocks, same with decoration entities (armor stands, paintings...)
        // 2. Compare existing with plan. Put valid blocks positions in variable toKeep
        // 3. Add existingEntities in entitiesToRemove
        // 4. Add schematic blocks in blocksToAdd only if position is not listed in toKeep
        // 5. Add schematic entities in entitiesToAdd
        List<ProjectStep> projectSteps = new ArrayList<>();
        if(!level.isClientSide()){
            if(level instanceof ServerLevel serverLevel){
                SchematicContent schematic = buildVariant.getSchematic(serverLevel.getServer().getResourceManager(), buildingLevel);
                Vec3i planDimensions = buildVariant.getSize();
                BlockPos firstCorner = buildingPlacementSettings.getPosition();
                BlockPos secondCorner = firstCorner.offset(planDimensions.getX(), planDimensions.getY(), planDimensions.getZ());
                List<BlockInfo> existingBlocks = new ArrayList<>();
                for (int y = firstCorner.getY(); y <= secondCorner.getY(); ++y) {
                    for (int x = firstCorner.getX(); x <= secondCorner.getX(); ++x) {
                        for (int z = firstCorner.getZ(); z <= secondCorner.getZ(); ++z) {
                            BlockPos blockPos = new BlockPos(x, y, z);
                            BlockState blockState = level.getBlockState(blockPos);
                            CompoundTag blockNbt = null;
                            BlockEntity blockEntity = level.getBlockEntity(blockPos);
                            /* TODO get BlockEntity NBT data.
                            if (blockEntity != null) {
                                blockNbt = blockEntity.getPersistentData();
                            }
                             */
                            existingBlocks.add(new BlockInfo(blockPos, blockState, blockNbt));
                        }
                    }
                }
                ConstructionUtils.sortBlocks(existingBlocks);
                List<BlockInfo> blocksToAdd = new ArrayList<>();
                List<BlockPos> blocksToRemove = new ArrayList<>();
                for (int i = 0; i < existingBlocks.size(); ++i) {
                    BlockState existingBlockState = existingBlocks.get(i).state();
                    CompoundTag existingBlockNbt = existingBlocks.get(i).nbt();
                    BlockState newBlockState = schematic.getBlockState(i);
                    CompoundTag newBlockNbt = schematic.getBlockNBT(i);
                    if (existingBlockState != newBlockState) {
                        if (newBlockState.isAir()) {
                            blocksToRemove.add(existingBlocks.get(i).pos());
                        }
                        blocksToAdd.add(existingBlocks.get(i));
                    }
                }
                List<EntityInfo> entitiesToRemove = new ArrayList<>();
                List<EntityInfo> entitiesToAdd = new ArrayList<>();

                blocksToRemove.forEach((blockPos -> projectSteps.add(new ProjectStep(StepType.REMOVE_BLOCK, blockPos, null, null, null, null))));
                blocksToAdd.forEach((blockInfo -> projectSteps.add(new ProjectStep(StepType.PLACE_BLOCK, blockInfo.pos(), blockInfo.state(),blockInfo.nbt(), null, null))));
            }
        }
        // TODO The project list is empty on Client side. Do we need to send some packet or is it OK ?
        return new ConstructionProject(level, name, projectType, buildVariant, buildingPlacementSettings, projectSteps);
    }

    public void executeNSteps(int times) {
        for (int i = 0; i < times; ++i) {
            executeNextStep();
        }
    }

    public boolean executeNextStep() {
        if (completed) {
            return false;
        }
        boolean success = false;
        ProjectStep nextStep = projectSteps.get(progress);
        BlockPos nextStepPos = ConstructionUtils.transformBlockPos(nextStep.blockPos, placementSettings.getMirror(), placementSettings.getRotation(), placementSettings.getRotationPivot()).offset(placementSettings.getPosition());
        BlockState nextStepState = ConstructionUtils.transformBlockState(nextStep.blockState, placementSettings.getMirror(), placementSettings.getRotation());
        Vec3 nextStepEntityPos = ConstructionUtils.transformEntityPos(nextStep.entityPos, placementSettings.getMirror(), placementSettings.getRotation(), placementSettings.getRotationPivot()).add(Vec3.atLowerCornerOf(placementSettings.getPosition()));

        switch (nextStep.type) {
            case REMOVE_BLOCK -> {
                success = level.destroyBlock(nextStep.blockPos, false);
            }
            case PLACE_BLOCK -> {
                success = level.setBlock(nextStepPos, nextStepState, 2);
                if (success) {
                    level.playSound(null, nextStepPos, nextStepState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                if (nextStep.blockNbt != null && !nextStep.blockNbt.isEmpty()) {
                    BlockEntity blockEntity = level.getBlockEntity(nextStepPos);
                    if (blockEntity != null) {
                        blockEntity.load(nextStep.blockNbt);
                    }
                }
            }
            case REMOVE_ENTITY -> {
                success = true;
            }
            case PLACE_ENTITY -> {
                CompoundTag newTag = nextStep.entityNbt.copy();
                ListTag entityPosTag = new ListTag();
                entityPosTag.add(DoubleTag.valueOf(nextStepEntityPos.x));
                entityPosTag.add(DoubleTag.valueOf(nextStepEntityPos.y));
                entityPosTag.add(DoubleTag.valueOf(nextStepEntityPos.z));
                newTag.put("Pos", entityPosTag);
                newTag.remove("UUID");
                var optional = createEntityIgnoreException((ServerLevelAccessor) level, newTag);
                if (optional.isPresent()) {
                    Entity entity = optional.get();
                    float f = entity.rotate(placementSettings.getRotation());
                    f += entity.mirror(placementSettings.getMirror()) - entity.getYRot();
                    entity.moveTo(nextStepEntityPos.x, nextStepEntityPos.y, nextStepEntityPos.z, f, entity.getXRot());
                    if (entity instanceof Mob mob) {
                        mob.finalizeSpawn((ServerLevelAccessor) level, level.getCurrentDifficultyAt(BlockPos.containing(nextStepEntityPos)), MobSpawnType.STRUCTURE,null, newTag);
                    }
                    ((ServerLevelAccessor) level).addFreshEntityWithPassengers(entity);
                    success = true;
                }
            }
        }
        if (success) {
            ++progress;
            if (progress >= projectSteps.size()) {
                completed = true;
            }
        }
        return success;
    }

    public ProjectStep getNextStep() {
        return projectSteps.get(progress);
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("Name", this.name);
        tag.putInt("Type", this.projectType.ordinal());
        tag.putInt("BuildX", this.placementSettings.getPosition().getX());
        tag.putInt("BuildY", this.placementSettings.getPosition().getY());
        tag.putInt("BuildZ", this.placementSettings.getPosition().getZ());
        tag.putString("Rotation", this.placementSettings.getRotation().getSerializedName());
        tag.putString("Mirror", this.placementSettings.getMirror().getSerializedName());
        return tag;
    }

    public BlockPos getPosition() {
        return this.placementSettings.getPosition();
    }

    public String getName() {
        return name;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    private static Optional<Entity> createEntityIgnoreException(ServerLevelAccessor level, CompoundTag tag) {
        try {
            return EntityType.create(tag, level.getLevel());
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    public enum ProjectType {
        NEW_BUILD,
        UPGRADE,
        REPAIR,
        DEMOLITION
    }

    public record ProjectStep(
            StepType type,
            BlockPos blockPos,
            BlockState blockState,
            CompoundTag blockNbt,
            Vec3 entityPos,
            CompoundTag entityNbt) {
    }

    public enum StepType {
        PLACE_BLOCK,
        REMOVE_BLOCK,
        PLACE_ENTITY,
        REMOVE_ENTITY}
}