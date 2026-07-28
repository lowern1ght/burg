package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CQuestUpdatePacket(CompoundTag data) implements CustomPacketPayload {

    public static final Type<S2CQuestUpdatePacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "s2c_quest_update"));

    public static final StreamCodec<FriendlyByteBuf, S2CQuestUpdatePacket> STREAM_CODEC =
        StreamCodec.of(S2CQuestUpdatePacket::write, S2CQuestUpdatePacket::read);

    private static S2CQuestUpdatePacket read(FriendlyByteBuf buf) {
        return new S2CQuestUpdatePacket(buf.readNbt());
    }

    private static void write(FriendlyByteBuf buf, S2CQuestUpdatePacket packet) {
        buf.writeNbt(packet.data());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CQuestUpdatePacket packet, IPayloadContext context) {
        context.enqueueWork(() ->
            TownHubClientState.pendingQuestUpdate = packet.data());
    }
}
