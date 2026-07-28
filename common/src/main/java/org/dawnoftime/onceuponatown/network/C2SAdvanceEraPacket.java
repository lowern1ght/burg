package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.tick.EraManager;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

public record C2SAdvanceEraPacket(BlockPos anchorPos, String pathId) implements CustomPacketPayload {

    public static final Type<C2SAdvanceEraPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_advance_era"));

    public static final StreamCodec<FriendlyByteBuf, C2SAdvanceEraPacket> STREAM_CODEC =
        StreamCodec.of(C2SAdvanceEraPacket::write, C2SAdvanceEraPacket::read);

    private static C2SAdvanceEraPacket read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String pathId = buf.readUtf();
        return new C2SAdvanceEraPacket(pos, pathId);
    }

    private static void write(FriendlyByteBuf buf, C2SAdvanceEraPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
        buf.writeUtf(packet.pathId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SAdvanceEraPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            EraManager.advance(town, packet.pathId(), level, packet.anchorPos());
        });
    }
}
