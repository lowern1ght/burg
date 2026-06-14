package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.town.LevelTowns;

public record C2SUpgradeBuildingPacket(BlockPos anchorPos, long buildingWorldPos) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_upgrade_building");

    public static C2SUpgradeBuildingPacket decode(FriendlyByteBuf buf) {
        return new C2SUpgradeBuildingPacket(buf.readBlockPos(), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeLong(buildingWorldPos);
    }

    public static class Handler {
        public static void handle(C2SUpgradeBuildingPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity anchor)) return;
            boolean queued = anchor.getTown().tryQueueUpgrade(BlockPos.of(packet.buildingWorldPos()));
            if (queued) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendBuildingListPacket.accept(player,
                    anchor.getTown().getBuildingListData(packet.anchorPos()));
                NetworkHelper.sendStockUpdatePacket.accept(player,
                    anchor.getTown().getStockUpdateData(packet.anchorPos()));
            }
        }
    }
}
