package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CEraUpdatePacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CEraUpdatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_era_update"));

    public static final StreamCodec<FriendlyByteBuf, S2CEraUpdatePacket> STREAM_CODEC =
        StreamCodec.of(S2CEraUpdatePacket::write, S2CEraUpdatePacket::read);

    private static S2CEraUpdatePacket read(FriendlyByteBuf buf) {
        return new S2CEraUpdatePacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CEraUpdatePacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CEraUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingEraUpdate = packet.data());
    }
}
