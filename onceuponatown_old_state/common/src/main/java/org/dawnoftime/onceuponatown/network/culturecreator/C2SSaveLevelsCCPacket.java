package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public record C2SSaveLevelsCCPacket(String cultureId, String buildingId, byte numberOfLevel) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_save_levels_cc");

    public static C2SSaveLevelsCCPacket decode(FriendlyByteBuf buf) {
        return new C2SSaveLevelsCCPacket(buf.readUtf(), buf.readUtf(), buf.readByte());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeByte(numberOfLevel);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                .resolve(MOD_ID)
                .resolve(CULTURES_FOLDER_NAME)
                .resolve(cultureId)
                .resolve(BUILDINGS_FOLDER_NAME)
                .resolve(buildingId + ".json");
        BuildingDataHandler data = new BuildingDataHandler(loadJson(jsonPath));
        data.resizeLevelLists(numberOfLevel);
        data.saveJson(jsonPath, player, cultureId);
    }
}
