package org.dawnoftime.onceuponatown.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class CultureCreatorEntity extends Entity {
    private static final EntityDataAccessor<BlockPos> DATA_SECOND_CORNER = SynchedEntityData.defineId(CultureCreatorEntity.class, EntityDataSerializers.BLOCK_POS);

    public CultureCreatorEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_SECOND_CORNER, BlockPos.ZERO);
    }

    public BlockPos getSecondPos() {
        return entityData.get(DATA_SECOND_CORNER);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }
}
