package org.lowern1ght.burg.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.client.TownHubClientState;

public record S2CStockUpdatePacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CStockUpdatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_stock_update"));

    public static final StreamCodec<FriendlyByteBuf, S2CStockUpdatePacket> STREAM_CODEC =
        StreamCodec.of(S2CStockUpdatePacket::write, S2CStockUpdatePacket::read);

    private static S2CStockUpdatePacket read(FriendlyByteBuf buf) {
        return new S2CStockUpdatePacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CStockUpdatePacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CStockUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingStockUpdate = packet.data());
    }
}
