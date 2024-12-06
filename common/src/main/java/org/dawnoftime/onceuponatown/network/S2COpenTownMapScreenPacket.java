package org.dawnoftime.onceuponatown.network;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.TownMapItemScreen;
import org.dawnoftime.onceuponatown.menu.SellMenu;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.util.Arrays;

import static org.dawnoftime.onceuponatown.Ouat.createOuatResource;

public record S2COpenTownMapScreenPacket(Component townName, float[][] map) implements IOuatPacket {
    public static final ResourceLocation ID = createOuatResource("s2c_open_town_map_screen");

    public static S2COpenTownMapScreenPacket decode(FriendlyByteBuf buf) {
        Component townName = buf.readComponent();
        int rows = buf.readInt();
        float[][] map = new float[rows][];
        for (int i = 0; i < rows; ++i) {
            int columns = buf.readInt();
            map[i] = new float[columns];
            for (int j = 0; j < columns; ++j) {
                map[i][j] = buf.readFloat(); // map block
            }
        }
        return new S2COpenTownMapScreenPacket(townName, map);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeComponent(townName);
        int rows = map.length;
        buf.writeInt(rows);
        for (int i = 0; i < rows; ++i) {
            int columns = map[i].length;
            buf.writeInt(columns);
            for (int j = 0; j < columns; ++j) {
                buf.writeFloat(map[i][j]); // map block
            }
        }
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenTownMapScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new TownMapItemScreen(packet.townName(), packet.map()));
            });
        }
    }
}