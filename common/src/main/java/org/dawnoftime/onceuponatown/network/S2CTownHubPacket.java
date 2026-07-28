package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CTownHubPacket(CompoundTag hubData) implements CustomPacketPayload {

    public static final Type<S2CTownHubPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_town_hub"));

    public static final StreamCodec<FriendlyByteBuf, S2CTownHubPacket> STREAM_CODEC =
        StreamCodec.of(S2CTownHubPacket::write, S2CTownHubPacket::read);

    private static S2CTownHubPacket read(FriendlyByteBuf buf) {
        return new S2CTownHubPacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CTownHubPacket packet) {
        buf.writeNbt(packet.hubData());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CTownHubPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingHubData = packet.hubData());
    }
}
