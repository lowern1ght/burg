package org.dawnoftime.onceuponatown.network;

import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.menu.TradeMenu;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SSelectTradePacket(int dealIndex) implements IOuatPacket {
    public static final ResourceLocation ID = modResource("c2s_select_trade");

    public static C2SSelectTradePacket decode(FriendlyByteBuf buf) {
        return new C2SSelectTradePacket(buf.readVarInt());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.dealIndex);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
}

    public void handle(MinecraftServer server, ServerPlayer player) {
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof TradeMenu tradeMenu) {
            if (!tradeMenu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, tradeMenu);
            } else {
                tradeMenu.selectDeal(this.dealIndex());
            }
        }
    }
}