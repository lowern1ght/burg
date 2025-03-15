package org.dawnoftime.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public abstract class NpcBaseMenu extends AbstractContainerMenu {
    protected InteractingNpc interactingNpc;

    protected NpcBaseMenu(@Nullable MenuType<?> menuType, int containerId, InteractingNpc interactingNpc) {
        super(menuType, containerId);
        this.interactingNpc = interactingNpc;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return null;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.interactingNpc.getInteractingPlayer() == player;
    }

    public InteractingNpc getNpcInteraction() {
        return interactingNpc;
    }
}
