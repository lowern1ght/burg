package org.dawnoftime.onceuponatown.network;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.gui.town.TownMapScreen;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record S2COpenTownMapScreenPacket(CompoundTag mapDataForGui) implements OuatPacket {
    public static final ResourceLocation ID = modResource("s2c_open_town_map_screen");

    public static S2COpenTownMapScreenPacket decode(FriendlyByteBuf buf) {
        return new S2COpenTownMapScreenPacket(buf.readNbt());
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
        public static void handle(S2COpenTownMapScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new TownMapScreen(packet.mapDataForGui()));
            });
        }
    }
}