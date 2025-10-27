package org.dawnoftime.onceuponatown.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.entity.CultureCreatorSelectEntity;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CultureCreatorBlockEntity extends BlockEntity {
    private String cultureId;
    private String buildingId;
    private String variantId;
    private int buildingLevel;
    private BlockPos secondPos = null;
    private final Map<BlockPos,String> waypoints = new HashMap<>();

    public CultureCreatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.REGISTRY.CULTURE_CREATOR.get(), pos, blockState);
    }

    public String getCultureId() {
        return cultureId;
    }

    public int getBuildingLevel() {
        return buildingLevel;
    }

    public String getVariantId() {
        return variantId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public void setParameters(@NotNull String cultureId, @NotNull String buildingId, @NotNull String variantId, int buildingLevel) {
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
        this.buildingLevel = buildingLevel;
    }

    public @Nullable BlockPos getSecondPos() {
        return this.secondPos;
    }

    public void setSecondPos(@NotNull BlockPos pos) {
        this.secondPos = pos;
        this.updateSelectEntity();
    }

    public void addWaypoint(BlockPos pos, String type) {

    }

    public void removeWaypoint(BlockPos pos) {

    }

    private void updateSelectEntity() {
        if (this.level != null && !this.level.isClientSide()) {
            CultureCreatorSelectEntity ccEntity = this.getFirstAndRemoveOthers(CultureCreatorSelectEntity.class, this.getBlockPos());
            if (ccEntity != null) {
                ccEntity.setPos(this.getBlockPos().getX() + 0.5F, this.getBlockPos().getY() + 0.5F, this.getBlockPos().getZ() + 0.5F);
                ccEntity.setSecondPos(this.secondPos);
            } else {
                ccEntity = new CultureCreatorSelectEntity(level, this.getBlockPos());
                ccEntity.setSecondPos(this.secondPos);
                this.level.addFreshEntity(ccEntity);
            }
        }
    }

    private void updateWaypointEntity(BlockPos waypointPos) {

    }

    private <T extends Entity> @Nullable T getFirstAndRemoveOthers(Class<T> entityClass, BlockPos pos) {
        if (this.level != null) {
            List<T> entityList = this.level.getEntitiesOfClass(entityClass, new AABB(pos));
            if (entityList.isEmpty()) {
                return null;
            }
            if (entityList.size() > 1) {
                for (int i = 1; i < entityList.size(); i++) {
                    entityList.get(i).discard();
                }
            }
            return entityList.get(0);
        }
        return null;
    }

    private <T extends Entity> void removeAllAt(Class<T> entityClass, BlockPos pos) {
        if (this.level != null) {
            List<T> entityList = this.level.getEntitiesOfClass(entityClass, new AABB(pos));
            for (T entity : entityList) {
                entity.discard();
            }
        }
    }

    @Override
    public void setLevel(@NotNull Level level) {
        super.setLevel(level);
        if (!level.isClientSide()) {
            this.updateSelectEntity();
        }
    }


    @Override
    public void load(@NotNull CompoundTag tag) {
        this.cultureId = tag.getString("culture_id");
        this.buildingId = tag.getString("building_id");
        this.variantId = tag.getString("variant_id");
        this.buildingLevel = tag.getInt("level");
        try {
            if (tag.contains("second_pos")) {
                this.secondPos = NbtUtils.readBlockPos(tag.getCompound("second_pos"));
            }
            this.waypoints.clear();
            if (tag.contains("waypoints", Tag.TAG_LIST)) {
                ListTag list = tag.getList("waypoints", Tag.TAG_COMPOUND);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag entryTag = list.getCompound(i);
                    BlockPos pos = NbtUtils.readBlockPos(entryTag.getCompound("pos"));
                    String label = entryTag.getString("label");
                    waypoints.put(pos, label);
                }
            }
        } catch (Exception e) {
            Ouat.error(e.getMessage());
        }
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        tag.putString("culture_id", this.cultureId);
        tag.putString("building_id", this.buildingId);
        tag.putString("variant_id", this.variantId);
        tag.putInt("level", this.buildingLevel);
        if (secondPos != null) {
            tag.put("second_pos", NbtUtils.writeBlockPos(secondPos));
        }
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, String> entry : waypoints.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
            entryTag.putString("label", entry.getValue());
            list.add(entryTag);
        }
        tag.put("waypoints", list);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null && !this.level.isClientSide()) {
            this.removeAllAt(CultureCreatorSelectEntity.class, this.getBlockPos());
//            for (BlockPos pos : this.waypoints.keySet()) {
//                this.removeAllAt(..., pos);
//            }
        }
    }

    public boolean forSameParameters(String cultureId, String buildingId, String variantId, int buildingLevel) {
        return this.cultureId.equals(cultureId) && this.buildingId.equals(buildingId) && this.variantId.equals(variantId) && this.buildingLevel == buildingLevel;
    }
}
