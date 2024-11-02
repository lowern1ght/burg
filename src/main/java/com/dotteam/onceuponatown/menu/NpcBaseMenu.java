package com.dotteam.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class NpcBaseMenu extends AbstractContainerMenu {
    protected NpcInteraction npc;

    protected NpcBaseMenu(@Nullable MenuType<?> menuType, int containerId, NpcInteraction npc) {
        super(menuType, containerId);
        this.npc = npc;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.npc.getInteractingPlayer() == player;
    }

    public NpcInteraction getNpc() {
        return npc;
    }
}
