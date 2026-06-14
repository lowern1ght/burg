package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

public record C2SRequestStockPacket(BlockPos anchorPos) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_request_stock");

    public static C2SRequestStockPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestStockPacket(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
    }

    public static class Handler {
        public static void handle(C2SRequestStockPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
        }
    }
}
