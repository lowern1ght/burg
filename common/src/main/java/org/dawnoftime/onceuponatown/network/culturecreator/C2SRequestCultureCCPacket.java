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
import static org.dawnoftime.onceuponatown.culture.ServerCultures.CULTURE_FOLDER_NAME;

public record C2SRequestCultureCCPacket(String cultureId) implements OuatPacket {
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
        try {
            Path newCultureFolder = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID).resolve(CULTURE_FOLDER_NAME).resolve(cultureId);
            Files.createDirectories(newCultureFolder);
            Ouat.COMMON.sendToClient(player, S2COpenCultureCCScreenPacket.create(player, cultureId));
        } catch (IOException e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while reading a culture file of '" + cultureId + "' : " + e);
        }
    }
}
