package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SRequestCulturesCCPacket() implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_cultures_cc");

    public static C2SRequestCulturesCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestCulturesCCPacket();
    }

    @Override
    public void encode(FriendlyByteBuf buf) {}

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        Ouat.COMMON.sendToClient(player, S2COpenCulturesCCScreenPacket.create(player));
    }
}
