package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SRequestCultureCCPacket(String cultureId) implements IOuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_culture_cc");

    public static C2SRequestCultureCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestCultureCCPacket(buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        Path newCultureFolder = Ouat.COMMON.getConfigFolder()
                .toPath()
                .resolve(MOD_ID)
                .resolve(cultureId);
        try {
            Ouat.clientChat(player, "cc", "error_culture_folder");
            Files.createDirectories(newCultureFolder);
            Ouat.COMMON.sendToClient(player, S2COpenCultureCCScreenPacket.create(cultureId));
        } catch (IOException e) {
            Ouat.clientChat(player, "cc", "error_culture_folder");
            Ouat.debug("An error occurred while reading a culture file : " + e);
        }
    }
}
