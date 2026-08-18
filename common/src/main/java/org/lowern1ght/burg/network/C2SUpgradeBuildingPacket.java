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

public record C2SUpgradeBuildingPacket(BlockPos anchorPos, long buildingWorldPos) implements CustomPacketPayload {

    public static final Type<C2SUpgradeBuildingPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_upgrade_building"));

    public static final StreamCodec<FriendlyByteBuf, C2SUpgradeBuildingPacket> STREAM_CODEC =
        StreamCodec.of(C2SUpgradeBuildingPacket::write, C2SUpgradeBuildingPacket::read);

    private static C2SUpgradeBuildingPacket read(FriendlyByteBuf buf) {
        return new C2SUpgradeBuildingPacket(buf.readBlockPos(), buf.readLong());
    }

    private static void write(FriendlyByteBuf buf, C2SUpgradeBuildingPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
        buf.writeLong(packet.buildingWorldPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SUpgradeBuildingPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            boolean queued = town.tryQueueUpgrade(BlockPos.of(packet.buildingWorldPos()));
            if (queued) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendBuildingListPacket.accept(player, town.getBuildingListData(packet.anchorPos()));
                NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
            }
        });
    }
}
