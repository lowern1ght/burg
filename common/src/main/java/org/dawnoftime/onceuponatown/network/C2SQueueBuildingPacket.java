package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.town.LevelTowns;

public record C2SQueueBuildingPacket(BlockPos anchorPos, String defId) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_queue_building");

    public static C2SQueueBuildingPacket decode(FriendlyByteBuf buf) {
        return new C2SQueueBuildingPacket(buf.readBlockPos(), buf.readUtf());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeUtf(defId);
    }

    public static class Handler {
        public static void handle(C2SQueueBuildingPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity anchor)) return;
            boolean added = anchor.getTown().tryAddToConstructionQueue(packet.defId());
            if (added) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendVillageHubPacket.accept(player,
                    anchor.getTown().getHubData(packet.anchorPos()));
            }
        }
    }
}
