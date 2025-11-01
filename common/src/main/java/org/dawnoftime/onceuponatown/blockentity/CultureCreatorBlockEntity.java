package org.dawnoftime.onceuponatown.blockentity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class CultureCreatorBlockEntity extends BlockEntity {
    private String cultureId = "";
    private String buildingId = "";
    private String variantId = "";
    private int buildingLevel = 0;
    private Component cultureComponent = Component.empty();
    private Component buildingComponent = Component.empty();
    private Component variantComponent = Component.empty();
    private Component buildingLevelComponent = Component.empty();
    private BlockPos size = null;
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

    public @NotNull Component getCultureComponent() {
        return cultureComponent;
    }

    public @NotNull Component getBuildingComponent() {
        return buildingComponent;
    }

    public @NotNull Component getVariantComponent() {
        return variantComponent;
    }

    public @NotNull Component getBuildingLevelComponent() {
        return buildingLevelComponent;
    }

    public void setParameters(@NotNull String cultureId, @NotNull String buildingId, @NotNull String variantId, int buildingLevel) {
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
        this.buildingLevel = buildingLevel;
        this.cultureComponent = Component.literal(this.cultureId).withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
        this.buildingComponent = Component.literal(this.buildingId).withStyle(ChatFormatting.BOLD);
        this.variantComponent = Component.literal(this.variantId);
        this.buildingLevelComponent = Component.literal("Level ").append(Component.literal(String.valueOf(this.buildingLevel)).withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW));
        this.setChanged();
    }

    public @Nullable BlockPos getSize() {
        return this.size;
    }

    public void setSize(@NotNull BlockPos secondPos) {
        this.size = new BlockPos(
                secondPos.getX() - this.worldPosition.getX(),
                secondPos.getY() - this.worldPosition.getY(),
                secondPos.getZ() - this.worldPosition.getZ()
        );
        this.setChanged();
    }

    public void addWaypoint(BlockPos pos, String type) {

    }

    public void removeWaypoint(BlockPos pos) {

    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }


    @Override
    public void load(@NotNull CompoundTag tag) {
        this.setParameters(tag.getString("culture_id"), tag.getString("building_id"), tag.getString("variant_id"), tag.getInt("level"));
        try {
            if (tag.contains("size")) {
                this.size = NbtUtils.readBlockPos(tag.getCompound("size"));
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
    public @NotNull CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
        tag.putString("culture_id", this.cultureId);
        tag.putString("building_id", this.buildingId);
        tag.putString("variant_id", this.variantId);
        tag.putInt("level", this.buildingLevel);
        if (size != null) {
            tag.put("size", NbtUtils.writeBlockPos(size));
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
}
