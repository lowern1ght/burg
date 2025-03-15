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
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public record C2SRequestBuildingsCCPacket(String cultureId) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_buildings_cc");

    public static C2SRequestBuildingsCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestBuildingsCCPacket(buf.readUtf());
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
        try {
            Path newCultureFolder = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId);
            Files.createDirectories(newCultureFolder);
            Ouat.COMMON.sendToClient(player, S2COpenBuildingsCCScreenPacket.create(player, cultureId));
        } catch (IOException e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while reading a culture file of '" + cultureId + "' : " + e);
        }
    }
}
