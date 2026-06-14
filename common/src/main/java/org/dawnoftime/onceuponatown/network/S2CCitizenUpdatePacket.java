package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CCitizenUpdatePacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_citizen_update");

    public static S2CCitizenUpdatePacket decode(FriendlyByteBuf buf) {
        return new S2CCitizenUpdatePacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CCitizenUpdatePacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                TownHubClientState.pendingCitizenUpdate = packet.data());
        }
    }
}
