package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CTownHubPacket(CompoundTag hubData) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_town_hub");

    public static S2CTownHubPacket decode(FriendlyByteBuf buf) {
        return new S2CTownHubPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(hubData);
    }

    public static class Handler {
        public static void handle(S2CTownHubPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                TownHubClientState.pendingHubData = packet.hubData());
        }
    }
}
