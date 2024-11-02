package com.dotteam.onceuponatown.network;

import com.dotteam.onceuponatown.client.screen.NpcBaseScreen;
import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.menu.BuyMenu;
import com.dotteam.onceuponatown.menu.NpcBaseMenu;
import com.dotteam.onceuponatown.menu.SellMenu;
import com.dotteam.onceuponatown.trade.TradeUtils;
import com.dotteam.onceuponatown.util.OuatLog;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

import java.util.function.Supplier;

public class C2SChangeNpcTabPacket {
    private final int newTab;

    public C2SChangeNpcTabPacket(int newTab) {
        this.newTab = newTab;
    }

    public C2SChangeNpcTabPacket(FriendlyByteBuf buffer) {
        this.newTab = buffer.readVarInt();
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.newTab);
    }

    public int getNewTab() {
        return this.newTab;
    }

    public boolean handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            NpcBaseScreen.NpcTab tab = NpcBaseScreen.NpcTab.values()[getNewTab()];
            AbstractContainerMenu containerMenu = context.getSender().containerMenu;
            if (containerMenu instanceof NpcBaseMenu menu) {
                if (!menu.stillValid( context.getSender())) {
                    Logger LOGGER = LogUtils.getLogger();
                    LOGGER.debug("Player {} interacted with invalid menu {}", context.getSender(), menu);
                }
                else {
                    Npc npc = menu.getNpc().getNpc();
                    switch (tab) {
                        case BUY -> {
                            NetworkHooks.openScreen(context.getSender(), new SimpleMenuProvider((containerID, playerInventory, p) -> new BuyMenu(containerID, playerInventory, npc), Component.literal("Buy")), buffer -> {
                                buffer.writeInt(npc.getId());
                                TradeUtils.writeBuyDealsToStream(npc.getBuyDeals(), buffer);
                                OuatLog.info("EXTRA DATA WRITTEN : " + buffer.readableBytes());
                            });
                        }
                        case SELL -> {
                            NetworkHooks.openScreen(context.getSender(), new SimpleMenuProvider((containerID, playerInventory, p) -> new SellMenu(containerID, playerInventory, npc), Component.literal("Sell")), buffer -> {
                                buffer.writeInt(npc.getId());
                                TradeUtils.writeSellDealsToStream(npc.getSellDeals(), buffer);
                                OuatLog.info("OPENED SELL SCREEN");
                            });
                        }
                    }
                }
            }
        });
        return true;
    }
}
