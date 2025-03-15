package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.BuildingCCScreen;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public class S2COpenBuildingCCScreenPacket implements OuatPacket {
    private static final ResourceLocation ID = modResource("s2c_open_building_screen_cc");

    private final String cultureId;
    private final String buildingId;

    private S2COpenBuildingCCScreenPacket(String cultureId, String buildingId){
        this.cultureId = cultureId;
        this.buildingId = buildingId;
    }

    public static S2COpenBuildingCCScreenPacket create(Player player, String cultureId, String buildingId){
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            String jsonContent = Files.readString(jsonPath);
            BuildingDataHandler data = new BuildingDataHandler(JsonParser.parseString(jsonContent).getAsJsonObject());
            // TODO Extract additional data.
        } catch (IOException ignored) {
            // An error occurred, we will create a new CULTURE_JSON_FILE_NAME
        }
        return new S2COpenBuildingCCScreenPacket(cultureId, buildingId);
    }

    public static S2COpenBuildingCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        return new S2COpenBuildingCCScreenPacket(cultureId, buildingId);
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

    public static class Handler {
        public static void handle(S2COpenBuildingCCScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new BuildingCCScreen(packet));
            });
        }
    }

    public String getCultureId(){
        return cultureId;
    }

    public String getBuildingId() {
        return buildingId;
    }
}