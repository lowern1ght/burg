package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.VillageHubClientState;

public record S2CVillageHubPacket(CompoundTag hubData) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_village_hub");

    public static S2CVillageHubPacket decode(FriendlyByteBuf buf) {
        return new S2CVillageHubPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(hubData);
    }

    public static class Handler {
        public static void handle(S2CVillageHubPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                VillageHubClientState.pendingHubData = packet.hubData());
        }
    }
}
