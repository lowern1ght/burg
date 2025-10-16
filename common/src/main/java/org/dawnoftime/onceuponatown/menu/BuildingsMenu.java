package org.dawnoftime.onceuponatown.menu;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.registry.MenuRegistry;
import org.jetbrains.annotations.Nullable;

public class BuildingsMenu extends NpcMenu {
    private final CompoundTag townMapData;

    public BuildingsMenu(int containerId, Inventory playerInventory, InteractingNpc npc, CompoundTag townMapData) {
        super(MenuRegistry.REGISTRY.BUILDINGS_MENU.get(), containerId, npc, playerInventory.player);
        this.townMapData = townMapData;
    }

    public BuildingsMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory,
            new InteractingNpcClient.Builder((Npc) (playerInventory.player.level().getEntity(buf.readInt())), playerInventory.player)
                .build(), buf.readNbt());
    }

    public @Nullable CompoundTag getTownMapData() {
        return townMapData;
    }
}
