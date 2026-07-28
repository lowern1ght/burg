package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CBuildingListPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CBuildingListPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_building_list"));

    public static final StreamCodec<FriendlyByteBuf, S2CBuildingListPacket> STREAM_CODEC =
        StreamCodec.of(S2CBuildingListPacket::write, S2CBuildingListPacket::read);

    private static S2CBuildingListPacket read(FriendlyByteBuf buf) {
        return new S2CBuildingListPacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CBuildingListPacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CBuildingListPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingBuildingList = packet.data());
    }
}
