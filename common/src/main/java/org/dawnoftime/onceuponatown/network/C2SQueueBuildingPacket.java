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

public record C2SQueueBuildingPacket(BlockPos anchorPos, String defId) implements CustomPacketPayload {

    public static final Type<C2SQueueBuildingPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_queue_building"));

    public static final StreamCodec<FriendlyByteBuf, C2SQueueBuildingPacket> STREAM_CODEC =
        StreamCodec.of(C2SQueueBuildingPacket::write, C2SQueueBuildingPacket::read);

    private static C2SQueueBuildingPacket read(FriendlyByteBuf buf) {
        return new C2SQueueBuildingPacket(buf.readBlockPos(), buf.readUtf());
    }

    private static void write(FriendlyByteBuf buf, C2SQueueBuildingPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
        buf.writeUtf(packet.defId());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SQueueBuildingPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            boolean added = town.tryAddToConstructionQueue(packet.defId());
            if (added) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendBuildingListPacket.accept(player, town.getBuildingListData(packet.anchorPos()));
                NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
                NetworkHelper.sendEraUpdatePacket.accept(player, town.getEraUpdateData(packet.anchorPos()));
            }
        });
    }
}
