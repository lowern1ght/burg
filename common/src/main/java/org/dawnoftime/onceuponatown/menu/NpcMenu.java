package org.dawnoftime.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class NpcMenu extends AbstractContainerMenu {
    protected InteractingNpc npc;

    protected NpcMenu(MenuType<?> menuType, int containerId, InteractingNpc npc, Player interactingPlayer) {
        super(menuType, containerId);
        this.npc = npc;
        npc.setInteractingPlayer(interactingPlayer);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        npc.setInteractingPlayer(null);
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return npc.getInteractingPlayer() == player;
    }

    public InteractingNpc getNpc() {
        return npc;
    }
}
