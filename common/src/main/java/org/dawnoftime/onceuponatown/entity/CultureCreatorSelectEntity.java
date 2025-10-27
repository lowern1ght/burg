package org.dawnoftime.onceuponatown.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CultureCreatorSelectEntity extends Entity {

    private static final EntityDataAccessor<BlockPos> DATA_SECOND_POS = SynchedEntityData.defineId(CultureCreatorSelectEntity.class, EntityDataSerializers.BLOCK_POS);

    public CultureCreatorSelectEntity(EntityType<CultureCreatorSelectEntity> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
    }

    public CultureCreatorSelectEntity(Level level, BlockPos source) {
        this(EntityRegistry.REGISTRY.CULTURE_CREATOR_SELECT_ENTITY.get(), level);
        this.setPos(source.getX() + 0.5, source.getY() + 0.5, source.getZ() + 0.5);
        this.setSecondPos(this.blockPosition());
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_SECOND_POS, BlockPos.ZERO);
    }

    public void setSecondPos(@Nullable BlockPos pos) {
        if (pos != null) {
            entityData.set(DATA_SECOND_POS, pos);
            // TODO Compute size to render is properly
        }
    }

    public @Nullable BlockPos getSecondPos() {
        BlockPos secondPos = entityData.get(DATA_SECOND_POS);
        return secondPos.equals(BlockPos.ZERO) ? null : secondPos;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("second_pos")) {
            this.setSecondPos(NbtUtils.readBlockPos(tag.getCompound("second_pos")));
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        BlockPos secondPos = this.getSecondPos();
        if (secondPos != null) {
            tag.put("second_pos", NbtUtils.writeBlockPos(secondPos));
        }
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doWaterSplashEffect() {}

    @Override
    public boolean shouldBeSaved() {
        return true;
    }
}
