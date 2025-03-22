package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.LevelsCCScreen;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenLevelsCCScreenPacket implements OuatPacket {
    private static final ResourceLocation ID = modResource("s2c_open_levels_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final int levelNumber;

    private S2COpenLevelsCCScreenPacket(String cultureId, String buildingId, int levelNumber){
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.levelNumber = levelNumber;
    }

    public static S2COpenLevelsCCScreenPacket create(Player player, String cultureId, String buildingId){
        int levelNumber = 1;
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            String jsonContent = Files.readString(jsonPath);
            BuildingDataHandler data = new BuildingDataHandler(JsonParser.parseString(jsonContent).getAsJsonObject());
            levelNumber = Math.max(1, data.levels.size());
        } catch (IOException ignored) {}
        return new S2COpenLevelsCCScreenPacket(cultureId, buildingId, levelNumber);
    }

    public static S2COpenLevelsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        int levelNumber = buf.readInt();
        return new S2COpenLevelsCCScreenPacket(cultureId, buildingId, levelNumber);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeInt(levelNumber);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public String getCultureId() {
        return cultureId;
    }

    public String getBuildingId() {
        return buildingId;
    }

    public int getLevelNumber() {
        return levelNumber;
    }

    public static class Handler {
        public static void handle(S2COpenLevelsCCScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new LevelsCCScreen(packet)));
            });
        }
    }
}