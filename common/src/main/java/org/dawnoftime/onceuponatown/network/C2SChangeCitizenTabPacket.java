package org.dawnoftime.onceuponatown.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.client.screen.CitizenBaseScreen;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.menu.BuyMenu;
import org.dawnoftime.onceuponatown.menu.CitizenBaseMenu;
import org.dawnoftime.onceuponatown.menu.SellMenu;
import org.dawnoftime.onceuponatown.platform.Platform;
import org.dawnoftime.onceuponatown.trade.TradeUtils;
import org.dawnoftime.onceuponatown.util.OuatLog;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.util.OuatUtils.createOuatResource;

public record C2SChangeCitizenTabPacket(int newTab) implements IOuatPacket {
    public static final ResourceLocation ID = createOuatResource("c2s_change_citizen_tab");

    public static C2SChangeCitizenTabPacket decode(FriendlyByteBuf buf) {
        return new C2SChangeCitizenTabPacket(buf.readVarInt());
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
        CitizenBaseScreen.CitizenTab tab = CitizenBaseScreen.CitizenTab.values()[this.newTab()];
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof CitizenBaseMenu menu) {
            if (!menu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, menu);
            }
            else {
                Citizen citizen = menu.getCitizen().getCitizen();
                switch (tab) {
                    case BUY -> {
                        Platform.PLATFORM.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) -> new BuyMenu(containerID, playerInventory, citizen), Component.literal("Buy")), buffer -> {
                            buffer.writeInt(citizen.getId());
                            TradeUtils.writeBuyDealsToStream(citizen.getBuyDeals(), buffer);
                            OuatLog.info("EXTRA DATA WRITTEN : " + buffer.readableBytes());
                        });
                    }
                    case SELL -> {
                        Platform.PLATFORM.openMenu(player, new SimpleMenuProvider((containerID, playerInventory, p) -> new SellMenu(containerID, playerInventory, citizen), Component.literal("Sell")), buffer -> {
                            buffer.writeInt(citizen.getId());
                            TradeUtils.writeSellDealsToStream(citizen.getSellDeals(), buffer);
                            OuatLog.info("OPENED SELL SCREEN");
                        });
                    }
                }
            }
        }
    }
}
