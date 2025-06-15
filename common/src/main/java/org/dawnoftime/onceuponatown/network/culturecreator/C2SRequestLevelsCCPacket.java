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
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public record C2SRequestLevelsCCPacket(String cultureId, String buildingId) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_levels_cc");

    public static C2SRequestLevelsCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestLevelsCCPacket(buf.readUtf(), buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
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
            if (Files.isDirectory(newCultureFolder)) {
                Ouat.COMMON.sendToClient(player, S2COpenLevelsCCScreenPacket.create(player, cultureId, buildingId));
                return;
            }
        } catch (Exception e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while reading a culture file of '" + cultureId + "' : " + e);
        }
        Ouat.COMMON.sendToClient(player, S2COpenCulturesCCScreenPacket.create(player));
    }
}
