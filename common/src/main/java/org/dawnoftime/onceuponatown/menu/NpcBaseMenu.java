package org.dawnoftime.onceuponatown.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class NpcBaseMenu extends AbstractContainerMenu {
    protected InteractingNpc npc;

    protected NpcBaseMenu(@Nullable MenuType<?> menuType, int containerId, InteractingNpc npc) {
        super(menuType, containerId);
        this.npc = npc;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return npc.getInteractingPlayer() == player;
    }

    public InteractingNpc getNpc() {
        return npc;
    }
}
