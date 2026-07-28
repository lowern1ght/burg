package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CLogEntryPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CLogEntryPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_log_entry"));

    public static final StreamCodec<FriendlyByteBuf, S2CLogEntryPacket> STREAM_CODEC =
        StreamCodec.of(S2CLogEntryPacket::write, S2CLogEntryPacket::read);

    private static S2CLogEntryPacket read(FriendlyByteBuf buf) {
        return new S2CLogEntryPacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CLogEntryPacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CLogEntryPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingLogEntry = packet.data());
    }
}
