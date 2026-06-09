package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.town.LevelTowns;

public record C2SAdvanceEraPacket(BlockPos anchorPos, String pathId) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_advance_era");

    public static C2SAdvanceEraPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String pathId = buf.readUtf();
        return new C2SAdvanceEraPacket(pos, pathId);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeUtf(pathId);
    }

    public static class Handler {
        public static void handle(C2SAdvanceEraPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity anchor)) return;
            boolean advanced = anchor.getTown().advanceEra(packet.pathId());
            if (advanced) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendVillageHubPacket.accept(player,
                    anchor.getTown().getHubData(packet.anchorPos()));
            }
        }
    }
}
