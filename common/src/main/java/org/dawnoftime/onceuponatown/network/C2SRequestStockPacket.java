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
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

public record C2SRequestStockPacket(BlockPos anchorPos) implements CustomPacketPayload {

    public static final Type<C2SRequestStockPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_request_stock"));

    public static final StreamCodec<FriendlyByteBuf, C2SRequestStockPacket> STREAM_CODEC =
        StreamCodec.of(C2SRequestStockPacket::write, C2SRequestStockPacket::read);

    private static C2SRequestStockPacket read(FriendlyByteBuf buf) {
        return new C2SRequestStockPacket(buf.readBlockPos());
    }

    private static void write(FriendlyByteBuf buf, C2SRequestStockPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestStockPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
        });
    }
}
