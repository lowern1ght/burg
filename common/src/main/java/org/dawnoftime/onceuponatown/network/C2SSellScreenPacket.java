package org.dawnoftime.onceuponatown.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.menu.SellMenu;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SSellScreenPacket(int dealIndex, RequestType requestType) implements IOuatPacket {
    public static final ResourceLocation ID = modResource("c2s_sell_screen");

    public static C2SSellScreenPacket decode(FriendlyByteBuf buf) {
        return new C2SSellScreenPacket(buf.readVarInt(), buf.readEnum(RequestType.class));
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(this.dealIndex);
        buf.writeEnum(this.requestType);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        AbstractContainerMenu containerMenu = player.containerMenu;
        if (containerMenu instanceof SellMenu sellMenu) {
            if (!sellMenu.stillValid(player)) {
                Logger LOGGER = LogUtils.getLogger();
                LOGGER.debug("Player {} interacted with invalid menu {}", player, sellMenu);
            }
            else {
                sellMenu.setSelectedDeal(this.dealIndex());
                sellMenu.handleClientAction(this.dealIndex(), this.requestType());
            }
        }
    }

    public enum RequestType {
        ONE_TRADE_SELL_ONE,
        ONE_TRADE_REMOVE_ONE,
        ONE_TRADE_SELL_EVERYTHING,
        ONE_TRADE_REMOVE_EVERYTHING,
        ALL_TRADES_SELL_EVERYTHING
    }
}