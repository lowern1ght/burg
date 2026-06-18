package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CLogEntryPacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_log_entry");

    public static S2CLogEntryPacket decode(FriendlyByteBuf buf) {
        return new S2CLogEntryPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CLogEntryPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                TownHubClientState.pendingLogEntry = packet.data());
        }
    }
}
