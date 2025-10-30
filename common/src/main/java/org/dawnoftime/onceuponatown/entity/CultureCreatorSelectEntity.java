package org.dawnoftime.onceuponatown.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
    private static final EntityDataAccessor<Component> DATA_CULTURE = SynchedEntityData.defineId(CultureCreatorSelectEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> DATA_BUILDING = SynchedEntityData.defineId(CultureCreatorSelectEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> DATA_VARIANT = SynchedEntityData.defineId(CultureCreatorSelectEntity.class, EntityDataSerializers.COMPONENT);
    private static final EntityDataAccessor<Component> DATA_LEVEL = SynchedEntityData.defineId(CultureCreatorSelectEntity.class, EntityDataSerializers.COMPONENT);

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
        this.entityData.define(DATA_SECOND_POS, BlockPos.ZERO);
        this.entityData.define(DATA_CULTURE, Component.empty());
        this.entityData.define(DATA_BUILDING, Component.empty());
        this.entityData.define(DATA_VARIANT, Component.empty());
        this.entityData.define(DATA_LEVEL, Component.empty());
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

    public void setText(@NotNull String cultureId, @NotNull String buildingId, @NotNull String variantId, int buildingLevel) {

        this.entityData.set(DATA_CULTURE, Component.literal(cultureId).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD));
        this.entityData.set(DATA_BUILDING, Component.literal(buildingId).withStyle(ChatFormatting.BOLD));
        this.entityData.set(DATA_VARIANT, Component.literal(variantId));
        this.entityData.set(DATA_LEVEL, Component.literal("Level ").append(Component.literal(String.valueOf(buildingLevel)).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW)));
    }

    public @NotNull Component getTextCulture() {
        return this.entityData.get(DATA_CULTURE);
    }

    public @NotNull Component getTextBuilding() {
        return this.entityData.get(DATA_BUILDING);
    }

    public @NotNull Component getTextVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public @NotNull Component getTextLevel() {
        return this.entityData.get(DATA_LEVEL);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        if (tag.contains("second_pos")) {
            this.setSecondPos(NbtUtils.readBlockPos(tag.getCompound("second_pos")));
        }
        Component text = Component.Serializer.fromJson(tag.getString("text_culture"));
        if (text != null) {
            this.entityData.set(DATA_CULTURE, text);
        }
        text = Component.Serializer.fromJson(tag.getString("text_building"));
        if (text != null) {
            this.entityData.set(DATA_BUILDING, text);
        }
        text = Component.Serializer.fromJson(tag.getString("text_variant"));
        if (text != null) {
            this.entityData.set(DATA_VARIANT, text);
        }
        text = Component.Serializer.fromJson(tag.getString("text_level"));
        if (text != null) {
            this.entityData.set(DATA_LEVEL, text);
        }
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        BlockPos secondPos = this.getSecondPos();
        if (secondPos != null) {
            tag.put("second_pos", NbtUtils.writeBlockPos(secondPos));
        }
        tag.putString("text_culture", Component.Serializer.toJson(this.getTextCulture()));
        tag.putString("text_building", Component.Serializer.toJson(this.getTextBuilding()));
        tag.putString("text_variant", Component.Serializer.toJson(this.getTextVariant()));
        tag.putString("text_level", Component.Serializer.toJson(this.getTextLevel()));
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
