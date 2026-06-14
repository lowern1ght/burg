package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CEraUpdatePacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_era_update");

    public static S2CEraUpdatePacket decode(FriendlyByteBuf buf) {
        return new S2CEraUpdatePacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CEraUpdatePacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                TownHubClientState.pendingEraUpdate = packet.data());
        }
    }
}
