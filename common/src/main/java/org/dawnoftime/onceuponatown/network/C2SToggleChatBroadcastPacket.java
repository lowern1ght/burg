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

public record C2SToggleChatBroadcastPacket(BlockPos anchorPos) implements CustomPacketPayload {

    public static final Type<C2SToggleChatBroadcastPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_toggle_chat_broadcast"));

    public static final StreamCodec<FriendlyByteBuf, C2SToggleChatBroadcastPacket> STREAM_CODEC =
        StreamCodec.of(C2SToggleChatBroadcastPacket::write, C2SToggleChatBroadcastPacket::read);

    private static C2SToggleChatBroadcastPacket read(FriendlyByteBuf buf) {
        return new C2SToggleChatBroadcastPacket(buf.readBlockPos());
    }

    private static void write(FriendlyByteBuf buf, C2SToggleChatBroadcastPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SToggleChatBroadcastPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            if (town.isChatSubscriber(player.getUUID())) {
                town.removeChatSubscriber(player.getUUID());
            } else {
                town.addChatSubscriber(player.getUUID());
            }
            LevelTowns.get(level).markDirty();
        });
    }
}
