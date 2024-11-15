package org.dawnoftime.onceuponatown.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.client.screen.TownMapItemScreen;
import org.dawnoftime.onceuponatown.menu.SellMenu;
import org.slf4j.Logger;

import static org.dawnoftime.onceuponatown.Ouat.createOuatResource;

public record S2COpenTownMapScreenPacket(int[] map) implements IOuatPacket {
    public static final ResourceLocation ID = createOuatResource("s2c_open_town_map_screen");

    public static S2COpenTownMapScreenPacket decode(FriendlyByteBuf buf) {
        return new S2COpenTownMapScreenPacket(buf.readVarIntArray());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeVarIntArray(map);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenTownMapScreenPacket packet) {
            int[] map = packet.map();
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new TownMapItemScreen(map));
            });
        }
    }
}