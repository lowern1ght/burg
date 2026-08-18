package org.lowern1ght.burg.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.client.ClientBuildingDefsRegistry;

public record S2CBuildingDefsPacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CBuildingDefsPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_building_defs"));

    public static final StreamCodec<FriendlyByteBuf, S2CBuildingDefsPacket> STREAM_CODEC =
        StreamCodec.of(S2CBuildingDefsPacket::write, S2CBuildingDefsPacket::read);

    private static S2CBuildingDefsPacket read(FriendlyByteBuf buf) {
        return new S2CBuildingDefsPacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CBuildingDefsPacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CBuildingDefsPacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            ClientBuildingDefsRegistry.setFromNbt(packet.data()));
    }
}
