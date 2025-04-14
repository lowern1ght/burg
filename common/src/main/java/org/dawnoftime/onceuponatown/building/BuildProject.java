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
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Optional;

public class BuildProject {
    protected final ServerLevel level;
    private final Type type;
    private final Town town;
    private final Build build;
    private final SchematicContent schematic;
    private int progress;
    private boolean rush;

    public BuildProject(ServerLevel level, Type type, Town town, Build build) {
        this.level = level;
        this.type = type;
        this.town = town;
        this.build = build;
        this.schematic = build.getSchematicContent(level.getServer().getResourceManager());
    }

    public static BuildProject load(ServerLevel level, Town town, CompoundTag tag) {
        Build build = town.getBuild(tag.getString("Build"));
        if (build != null) {
            BuildProject project = new BuildProject(level, Type.valueOf(tag.getString("Type")), town, build);
            project.progress = tag.getInt("Progress");
            project.rush = tag.getBoolean("Rush");
            return project;
        }
        return null;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putString("Type", type.name());
        tag.putString("Build", build.toSafeString());
        tag.putInt("Progress", progress);
        tag.putBoolean("Rush", rush);
        return tag;
    }

    public boolean nextStep() {
        if (isCompleted()) {
            return false;
        }
        return placingBlocksPhase() ? nextBlockStep() : nextEntityStep();
    }

    private boolean nextBlockStep() {
        boolean success = false;
        BlockPos nextStepPos = ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos());
        BlockState nextStepState = ConstructionUtils.transformBlockState(schematic.getBlockState(progress), build.getMirror(), build.getRotation());
        CompoundTag nextStepNbt = schematic.getBlockNBT(progress);

        BlockState stateAtPos = level.getBlockState(nextStepPos);
        if (stateAtPos != nextStepState) {
            if (!stateAtPos.isAir() && !stateAtPos.is(Blocks.WATER) && !stateAtPos.is(Blocks.LAVA)) {
                success = level.destroyBlock(nextStepPos, false);
            } else if (level.getEntitiesOfClass(LivingEntity.class, new AABB(nextStepPos)).isEmpty()) {
                success = level.setBlock(nextStepPos, nextStepState, 2);
                if (success) {
                    level.playSound(null, nextStepPos, nextStepState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
                    if (nextStepNbt != null && !nextStepNbt.isEmpty()) {
                        BlockEntity blockEntity = level.getBlockEntity(nextStepPos);
                        if (blockEntity != null) {
                            blockEntity.load(nextStepNbt);
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

    private boolean nextEntityStep() {
        boolean success = false;
        int index = progress - schematic.getBlocks().size();
        Vec3 nextStepPos = ConstructionUtils.transformEntityPos(schematic.getEntities().get(index).vec3(), build.getMirror(), build.getRotation(), BlockPos.ZERO).add(Vec3.atLowerCornerOf(build.getOriginPos()));
        CompoundTag nextStepNbt = schematic.getEntities().get(index).entityNbt().copy();

        ListTag entityPosTag = new ListTag();
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.x));
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.y));
        entityPosTag.add(DoubleTag.valueOf(nextStepPos.z));
        nextStepNbt.put("Pos", entityPosTag);
        nextStepNbt.remove("UUID");
        Optional<Entity> opt;
        try {
            opt = EntityType.create(nextStepNbt, level);
        } catch (Exception exception) {
            opt = Optional.empty();
        }
        if (opt.isPresent()) {
            Entity entity = opt.get();
            float f = entity.rotate(build.getRotation());
            f += entity.mirror(build.getMirror()) - entity.getYRot();
            entity.moveTo(nextStepPos.x, nextStepPos.y, nextStepPos.z, f, entity.getXRot());
            if (entity instanceof Mob mob) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(nextStepPos)), MobSpawnType.STRUCTURE, null, nextStepNbt);
            }
            level.addFreshEntityWithPassengers(entity);
            ++progress;
            success = true;
        }
        checkCompleted();
        return success;
    }

    public Action getNextAction() {
        if (!isCompleted()) {
            if (placingBlocksPhase()) {
                BlockPos nextStepPos = ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos());
                BlockState nextStepState = ConstructionUtils.transformBlockState(schematic.getBlockState(progress), build.getMirror(), build.getRotation());

                BlockState stateAtPos = level.getBlockState(nextStepPos);
                if (stateAtPos != nextStepState) {
                    if (stateAtPos.isAir() || stateAtPos.is(Blocks.WATER) || stateAtPos.is(Blocks.LAVA)) {
                        return Action.PLACE_BLOCK;
                    } else {
                        return Action.DESTROY_BLOCK;
                    }
                } else {
                    return Action.NOTHING;
                }
            } else {
                return Action.SPAWN_ENTITY;
            }
        }
        return null;
    }

    public void nextNSteps(int times) {
        for (int i = 0; i < times; ++i) {
            nextStep();
        }
    }

    public boolean tryFinish() {
        for (int i = 0; i < 100000 && !isCompleted(); ++i) {
            nextStep();
        }
        return isCompleted();
    }

    private void checkCompleted() {
        if (isCompleted()) {
            build.setStatus(Build.Status.COMPLETED);
            town.notifyProjectCompleted(this);
        }
    }

    public BlockPos getNextStepPos() {
        if (!isCompleted()) {
            if (placingBlocksPhase()) {
                return ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos());
            } else {
                int index = progress - schematic.getBlocks().size();
                Vec3 nextStepPos = ConstructionUtils.transformEntityPos(schematic.getEntities().get(index).vec3(), build.getMirror(), build.getRotation(), BlockPos.ZERO).add(Vec3.atLowerCornerOf(build.getOriginPos()));
                return BlockPos.containing(nextStepPos.x, nextStepPos.y, nextStepPos.z);

            }
        }
        return null;
    }

    public BlockState getNextStepState() {
        if (!isCompleted() && placingBlocksPhase()) {
            BlockState stateAtPos = level.getBlockState(ConstructionUtils.transformBlockPos(schematic.getBlockPos(progress), build.getMirror(), build.getRotation(), BlockPos.ZERO).offset(build.getOriginPos()));
            BlockState nextStepState = ConstructionUtils.transformBlockState(schematic.getBlockState(progress), build.getMirror(), build.getRotation());
            return (stateAtPos == nextStepState) ? stateAtPos : nextStepState;
        }
        return null;
    }

    private boolean placingBlocksPhase() {
        return progress < schematic.getBlocks().size();
    }

    public void rush(boolean rush) {
        this.rush = rush;
    }

    public boolean rushing() {
        return rush;
    }

    public BlockPos getOriginPos() {
        return build.getOriginPos();
    }

    public boolean isCompleted() {
        return progress >= schematic.getBlocks().size() + schematic.getEntities().size();
    }

    public boolean isAvailable() {
        return !isCompleted() && !rush;
    }

    public String toSafeString() {
        return build.toSafeString();
    }

    public Type getProjectType() {
        return type;
    }

    public String getProgression() {
        return progress + "/" + (schematic.getBlocks().size() + schematic.getEntities().size());
    }

    public BoundingBox getBoundingBox() {
        BlockPos originPos = build.getOriginPos();
        BlockPos cornerPos = originPos.offset(build.getSizeX(), build.getSizeY(), build.getSizeZ());
        return new BoundingBox(originPos.getX(), originPos.getY(), originPos.getZ(), cornerPos.getX(), cornerPos.getY(), cornerPos.getZ());
    }

    public enum Type {
        NEW_BUILD("build being built"),
        UPGRADE("build being upgraded"),
        REPAIR("build beeing repaired");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum Action {
        PLACE_BLOCK,
        DESTROY_BLOCK,
        SPAWN_ENTITY,
        NOTHING
    }
}