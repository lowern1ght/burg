package org.dawnoftime.onceuponatown.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.registry.BlockEntityRegistry;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;
import org.dawnoftime.onceuponatown.town.Town;

public class TownAnchorBlockEntity extends BlockEntity implements MenuProvider {
    private Town town = new Town();

    public TownAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.TOWN_ANCHOR, pos, state);
    }

    public Town getTown() { return town; }

    public void setTown(Town town) {
        this.town = town;
        setChanged();
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Village Chest");
    }

    @Override
    public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
        return new VillageChestMenu(syncId, playerInventory, town);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Town", town.toNbt());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Town")) {
            this.town = Town.fromNbt(tag.getCompound("Town"));
        }
    }
}
