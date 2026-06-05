package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.ClientUtils;

import static org.dawnoftime.onceuponatown.Ouat.COMMON;
import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record S2CTownScrollScreenPacket(CompoundTag mapDataForGui) implements OuatPacket {
    public static final ResourceLocation ID = modResource("s2c_town_scroll_screen");

    public static S2CTownScrollScreenPacket decode(FriendlyByteBuf buf) {
        return new S2CTownScrollScreenPacket(buf.readNbt());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(mapDataForGui);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2CTownScrollScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openTownMapItemScreen(packet.mapDataForGui());
            }
        }
    }
}