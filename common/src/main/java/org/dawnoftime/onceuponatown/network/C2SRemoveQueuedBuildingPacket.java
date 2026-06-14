package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.town.LevelTowns;

public record C2SRemoveQueuedBuildingPacket(BlockPos anchorPos, int slotIndex) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_remove_queued_building");

    public static C2SRemoveQueuedBuildingPacket decode(FriendlyByteBuf buf) {
        return new C2SRemoveQueuedBuildingPacket(buf.readBlockPos(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeInt(slotIndex);
    }

    public static class Handler {
        public static void handle(C2SRemoveQueuedBuildingPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity anchor)) return;
            boolean removed = anchor.getTown().removeFromConstructionQueue(packet.slotIndex());
            if (removed) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendBuildingListPacket.accept(player,
                    anchor.getTown().getBuildingListData(packet.anchorPos()));
                NetworkHelper.sendStockUpdatePacket.accept(player,
                    anchor.getTown().getStockUpdateData(packet.anchorPos()));
                NetworkHelper.sendEraUpdatePacket.accept(player,
                    anchor.getTown().getEraUpdateData(packet.anchorPos()));
            }
        }
    }
}
