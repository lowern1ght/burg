package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public record C2SSaveBuildingCCPacket(String cultureId, String buildingId, String itemId, String weight) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_building_cc");

    public static C2SSaveBuildingCCPacket decode(FriendlyByteBuf buf) {
        return new C2SSaveBuildingCCPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(itemId);
        buf.writeUtf(weight);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        JsonObject json;
        Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                .resolve(MOD_ID)
                .resolve(CULTURES_FOLDER_NAME)
                .resolve(cultureId)
                .resolve(BUILDINGS_FOLDER_NAME)
                .resolve(buildingId + ".json");
        try {
            String jsonContent = Files.readString(jsonPath);
            json = JsonParser.parseString(jsonContent).getAsJsonObject();
        } catch (Exception ignored) {
            // An error occurred, we will create a new building.json file.
            json = new JsonObject();
        }
        BuildingDataHandler data = new BuildingDataHandler(json);
        data.item.set(itemId);
        data.weight.set(weight);
        JsonObject editedJson = data.toJson(new JsonObject());
        try {
            Files.createDirectories(jsonPath.getParent());
            Files.writeString(jsonPath, editedJson.toString(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while saving a building file of '" + cultureId + "' : " + e);
        }
    }
}
