package org.dawnoftime.onceuponatown.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.NbtPreviewClientState;

public record S2CNbtPreviewPacket(CompoundTag data) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_nbt_preview");

    public static S2CNbtPreviewPacket decode(FriendlyByteBuf buf) {
        return new S2CNbtPreviewPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(data);
    }

    public static class Handler {
        public static void handle(S2CNbtPreviewPacket packet) {
            Minecraft.getInstance().execute(() ->
                NbtPreviewClientState.open(packet.data()));
        }
    }
}
