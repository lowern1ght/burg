package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.blockentity.TownAnchorBlockEntity;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

public record C2SRemoveQueuedBuildingPacket(BlockPos anchorPos, int slotIndex) implements CustomPacketPayload {

    public static final Type<C2SRemoveQueuedBuildingPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_remove_queued_building"));

    public static final StreamCodec<FriendlyByteBuf, C2SRemoveQueuedBuildingPacket> STREAM_CODEC =
        StreamCodec.of(C2SRemoveQueuedBuildingPacket::write, C2SRemoveQueuedBuildingPacket::read);

    private static C2SRemoveQueuedBuildingPacket read(FriendlyByteBuf buf) {
        return new C2SRemoveQueuedBuildingPacket(buf.readBlockPos(), buf.readInt());
    }

    private static void write(FriendlyByteBuf buf, C2SRemoveQueuedBuildingPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
        buf.writeInt(packet.slotIndex());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRemoveQueuedBuildingPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            boolean removed = town.removeFromConstructionQueue(packet.slotIndex());
            if (removed) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendBuildingListPacket.accept(player, town.getBuildingListData(packet.anchorPos()));
                NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
                NetworkHelper.sendEraUpdatePacket.accept(player, town.getEraUpdateData(packet.anchorPos()));
            }
        });
    }
}
