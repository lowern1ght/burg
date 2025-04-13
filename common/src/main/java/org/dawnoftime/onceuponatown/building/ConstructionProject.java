package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Optional;

public class ConstructionProject {
    protected final ServerLevel level;
    private final ProjectType projectType;
    private final Build build;
    private final SchematicContent schematic;
    private int progress;
    private boolean rush;

    public ConstructionProject(ServerLevel level, ProjectType projectType, Build build) {
        this.level = level;
        this.projectType = projectType;
        this.build = build;
        this.schematic = build.getSchematicContent(level.getServer().getResourceManager());
    }

    public static ConstructionProject load(ServerLevel level, Town town, CompoundTag projectTag) {
        Build build = town.getBuild(projectTag.getString("Build"));
        if (build != null) {
            ConstructionProject project = new ConstructionProject(level, ProjectType.valueOf(projectTag.getString("Type")), build);
            project.progress = projectTag.getInt("Progress");
            project.rush = projectTag.getBoolean("Rush");
            return project;
        }
        return null;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("Type", projectType.name());
        tag.putString("Build", build.toSafeString());
        tag.putInt("Progress", progress);
        tag.putBoolean("Rush", rush);
        return tag;
    }

    public boolean tryFinish() {
        for (int i = 0; i < 100000 && !isCompleted(); ++i) {
            nextStep();
        }
        return isCompleted();
    }

    public void nextNSteps(int times) {
        for (int i = 0; i < times; ++i) {
            nextStep();
        }
    }

    private boolean nextEntityStep() {
        int index = progress - schematic.getBlocks().size();
        Vec3 nextStepPos = ConstructionUtils.transformEntityPos(schematic.getEntities().get(index).vec3(), build.getMirror(), build.getRotation(), BlockPos.ZERO).add(Vec3.atLowerCornerOf(build.getOriginPos()));
        boolean success = false;
        CompoundTag newTag = schematic.getEntities().get(index).entityNbt().copy();
        ListTag entityPosTag = new ListTag();
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.x));
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.y));
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.z));
        newTag.put("Pos", entityPosTag);
        newTag.remove("UUID");
        Optional<Entity> opt;
        try {
            opt = EntityType.create(newTag, level);
        } catch (Exception exception) {
            opt = Optional.empty();
        }
        if (opt.isPresent()) {
            Entity entity = opt.get();
            float f = entity.rotate(build.getRotation());
            f += entity.mirror(build.getMirror()) - entity.getYRot();
            entity.moveTo(nextStepPos.x, nextStepPos.y, nextStepPos.z, f, entity.getXRot());
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(nextStepPos)), MobSpawnType.STRUCTURE, null, newTag);
            }
            level.addFreshEntityWithPassengers(entity);
            ++progress;
            success = true;
        }
        checkCompleted();
        return success;
    }

    private boolean nextBlockStep() {
        BlockPos nextStepPos = ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos());
        BlockState nextStepState = ConstructionUtils.transformBlockState(schematic.getBlockState(progress), build.getMirror(), build.getRotation());
        CompoundTag blockNbt = schematic.getBlockNBT(progress);
        boolean success = false;
        BlockState stateAtPos = level.getBlockState(nextStepPos);
        if (stateAtPos != nextStepState) {
            if (!stateAtPos.isAir() && stateAtPos.getBlock() != Blocks.WATER) {
                success = level.destroyBlock(nextStepPos, false);
            } else if (level.getEntitiesOfClass(LivingEntity.class, AABB.ofSize(nextStepPos.getCenter(), 2.0D, 2.0D, 2.0D)).isEmpty()) {
                success = level.setBlock(nextStepPos, nextStepState, 2);
                if (success) {
                    level.playSound(null, nextStepPos, nextStepState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (blockNbt != null && !blockNbt.isEmpty()) {
                        BlockEntity blockEntity = level.getBlockEntity(nextStepPos);
                        if (blockEntity != null) {
                            blockEntity.load(blockNbt);
                        }
                    }
                    ++progress;
                }
            }
        } else {
            ++progress;
            success = true;
        }
        checkCompleted();
        return success;
    }

    private void checkCompleted() {
        if (isCompleted()) {
            build.setStatus(Build.Status.COMPLETED);
        }
    }

    public boolean nextStep() {
        if (isCompleted()) {
            return false;
        }
        if (progress >= schematic.getBlocks().size()) {
            return nextEntityStep();
        } else {
            return nextBlockStep();
        }
    }

    public NextAction getNextStepType() {
        if (!isCompleted()) {
            if (progress >= schematic.getBlocks().size()) {
                return NextAction.SPAWN_ENTITY;
            } else {
                BlockPos nextStepPos = ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos());
                BlockState nextStepState = ConstructionUtils.transformBlockState(schematic.getBlockState(progress), build.getMirror(), build.getRotation());
                BlockState stateAtPos = level.getBlockState(nextStepPos);
                if (stateAtPos != nextStepState) {
                    if (stateAtPos.isAir()) {
                        return NextAction.DESTROY_BLOCK;
                    } else {
                        return NextAction.PLACE_BLOCK;
                    }
                } else {
                    return NextAction.NOTHING;
                }
            }
        }
        return null;
    }

    public void rush(boolean rush) {
        this.rush = rush;
    }

    public boolean rushing() {
        return rush;
    }

    public BlockPos getNextStepPos() {
        if (!isCompleted()) {
            //ProjectStep nextStep = projectSteps.get(progress);
            return null;//ConstructionUtils.transformBlockPos(nextStep.blockPos, this.build.getMirror(), this.build.getRotation(), BlockPos.ZERO).offset(this.build.getOriginPos());
        } else {
            return null;
        }
    }

    public BlockState getNextStepState() {
        if (!isCompleted()) {
            //ProjectStep nextStep = projectSteps.get(progress);
            return null;//ConstructionUtils.transformBlockState(nextStep.blockState, this.build.getMirror(), this.build.getRotation());
        } else {
            return null;
        }
    }

    public BlockPos getPosition() {
        return build.getOriginPos();
    }

    public boolean isCompleted() {
        return progress >= schematic.getBlocks().size() + schematic.getEntities().size();
    }

    public String toSafeString() {
        return build.toSafeString();
    }

    public ProjectType getProjectType() {
        return projectType;
    }

    public String getProgression() {
        return progress + "/" + (schematic.getBlocks().size() + schematic.getEntities().size());
    }

    public enum ProjectType {
        NEW_BUILD("building under construction"),
        UPGRADE("building being upgraded"),
        REPAIR("building beeing repaired");

        final String description;

        ProjectType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum NextAction {
        PLACE_BLOCK,
        DESTROY_BLOCK,
        SPAWN_ENTITY,
        NOTHING
    }
}