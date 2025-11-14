package org.dawnoftime.onceuponatown.blockentity;

import com.google.gson.JsonObject;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.apache.logging.log4j.core.jmx.Server;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.Waypoint;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public class CultureCreatorBlockEntity extends BlockEntity {
    private String cultureId = "";
    private String buildingId = "";
    private String variantId = "";
    private int buildingLevel = 0;
    private Component cultureComponent = Component.empty();
    private Component buildingComponent = Component.empty();
    private Component variantComponent = Component.empty();
    private Component buildingLevelComponent = Component.empty();
    private BlockPos size = new BlockPos(1, 0, 1);
    private final Map<BlockPos, Waypoint> waypoints = new HashMap<>();

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

    public Map<BlockPos, Waypoint> getWaypoints() {
        return waypoints;
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

    public @NotNull BlockPos getSize() {
        return this.size;
    }

    public void setSize(ServerPlayer player, @NotNull BlockPos secondPos) {
        int xSize = secondPos.getX() - this.worldPosition.getX();
        int zSize = secondPos.getZ() - this.worldPosition.getZ();
        if (xSize <= 0 || zSize <= 0) {
            Ouat.clientChat(player, "cc", "error_second_pos_out_of_area");
        }
        this.size = new BlockPos(Math.max(xSize, 1), secondPos.getY() - this.worldPosition.getY(), Math.max(zSize, 1));
        this.setChanged();
    }

    public void addWaypoint(BlockPos pos, String type) {

    }

    public void removeWaypoint(BlockPos pos) {

    }

    public boolean saveBuilding(ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel) {
            StructureTemplate template = new StructureTemplate();
            template.fillFromWorld(serverLevel, this.getBlockPos(), this.getSize(), true, Blocks.STRUCTURE_VOID);
            template.save(new CompoundTag());

            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            BuildingDataHandler data = new BuildingDataHandler(loadJson(jsonPath));
            Optional<BuildingDataHandler.BuildingVariantHandler> variantOpt = data.variants.stream()
                    .filter(variant -> variant.name.asString().equals(variantId))
                    .findFirst();
            if (variantOpt.isEmpty()) {
                return false;
            }
            BuildingDataHandler.BuildingVariantHandler variant = variantOpt.get();
            if (variant.levels.size() < this.buildingLevel) {
                return false;
            }
            BuildingDataHandler.BuildingVariantLevelHandler buildingLevel = variant.levels.get(this.buildingLevel - 1);
            for (Map.Entry<BlockPos, Waypoint> entry : waypoints.entrySet()) {
                BuildingDataHandler.WaypointHandler wp = new BuildingDataHandler.WaypointHandler(new JsonObject());
                wp.id.set(entry.getValue().name());
                wp.x.set(String.valueOf(entry.getKey().getX()));
                wp.y.set(String.valueOf(entry.getKey().getY()));
                wp.z.set(String.valueOf(entry.getKey().getZ()));
                buildingLevel.waypoints.add(wp);
            }
            data.saveJson(jsonPath, player, cultureId);
        }
        return false;
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
                    if (Waypoint.exists(label)) {
                        waypoints.put(pos, Waypoint.valueOf(label));
                    }
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
        tag.put("size", NbtUtils.writeBlockPos(this.size));
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Waypoint> entry : waypoints.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
            entryTag.putString("label", entry.getValue().name());
            list.add(entryTag);
        }
        tag.put("waypoints", list);
    }
}
