package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.io.IOException;
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
        try {
            Path newCultureFolder = Ouat.COMMON.getConfigFolder()
                    .toPath()
                    .resolve(MOD_ID);
            Files.createDirectories(newCultureFolder);
            Ouat.COMMON.sendToClient(player, S2COpenCulturesCCScreenPacket.create(player));
        } catch (IOException e) {
            Ouat.clientChat(player, "cc", "cultures_error");
            Ouat.debug("An error occurred while reading the cultures folder : " + e);
        }
    }
}
