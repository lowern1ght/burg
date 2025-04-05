package org.dawnoftime.onceuponatown.network.inventory;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.gui.npc.NpcBaseScreen;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.menu.NpcBaseMenu;
import org.dawnoftime.onceuponatown.menu.TradeMenu;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SChangeNpcTabPacket(int newTab) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_change_npc_tab");

    public static C2SChangeNpcTabPacket decode(FriendlyByteBuf buf) {
        return new C2SChangeNpcTabPacket(buf.readVarInt());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.newTab);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        NpcBaseScreen.TabType tab = NpcBaseScreen.TabType.values()[this.newTab()];
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof NpcBaseMenu menu) {
            if (!menu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, menu);
            } else {
                Npc npc = menu.getNpc().getNpc();
                switch (tab) {
                    case TRADE -> {
                        Ouat.COMMON.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) -> new TradeMenu(containerID, playerInventory, npc), Component.literal("Trade")), buffer -> {
                            buffer.writeInt(npc.getId());
                            TradeUtils.writeOffersToStream(npc.getOffers(), buffer);
                        });
                    }
                    case QUESTS -> {

                    }
                }
            }
        }
    }
}
