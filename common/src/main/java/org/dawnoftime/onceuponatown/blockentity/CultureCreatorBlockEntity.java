package org.dawnoftime.onceuponatown.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class CultureCreatorBlockEntity extends BlockEntity {
    private BlockPos secondPos = null;
    private final Map<BlockPos,String> waypoints = new HashMap<>();

    public CultureCreatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.REGISTRY.CULTURE_CREATOR.get(), pos, blockState);
    }

    public void setSecondPos(BlockPos pos) {
        secondPos = pos;
    }

    public BlockPos getSecondPos() {
        return secondPos;
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
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
        } catch (Exception ignored) {}
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag tag) {
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
}
