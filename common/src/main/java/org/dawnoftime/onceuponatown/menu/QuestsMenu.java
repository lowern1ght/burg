package org.dawnoftime.onceuponatown.menu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;

public class QuestsMenu extends NpcBaseMenu {
    public QuestsMenu(int containerId, Inventory playerInventory, InteractingNpc npc) {
        super(MenuRegistry.REGISTRY.QUESTS_MENU.get(), containerId, npc, playerInventory.player);
    }

    public QuestsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
            new InteractingNpcClient.Builder((Npc) (playerInventory.player.level().getEntity(buf.readInt())), playerInventory.player)
                .build());
    }
}
