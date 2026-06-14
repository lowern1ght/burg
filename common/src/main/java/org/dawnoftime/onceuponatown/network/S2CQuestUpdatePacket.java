package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.TownHubClientState;

public record S2CQuestUpdatePacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_quest_update");

    public static S2CQuestUpdatePacket decode(FriendlyByteBuf buf) {
        return new S2CQuestUpdatePacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CQuestUpdatePacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                TownHubClientState.pendingQuestUpdate = packet.data());
        }
    }
}
