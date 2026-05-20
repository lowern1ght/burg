package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;

public record S2CTownScrollScreenPacket(CompoundTag mapData) {
    public static final ResourceLocation ID = Ouat.modResource("s2c_town_scroll_screen");

    public static S2CTownScrollScreenPacket decode(FriendlyByteBuf buf) {
        return new S2CTownScrollScreenPacket(buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(mapData);
    }

    public static class Handler {
        public static void handle(S2CTownScrollScreenPacket packet) {
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                net.minecraft.client.Minecraft.getInstance().setScreen(
                    new org.dawnoftime.onceuponatown.client.gui.screens.TownScrollScreen(packet.mapData())));
        }
    }
}
