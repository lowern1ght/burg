package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenLevelsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_levels_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final int levelNumber;

    private S2COpenLevelsCCScreenPacket(String cultureId, String buildingId, int levelNumber){
        super(ID.getPath());
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
        S2COpenLevelsCCScreenPacket packet = new S2COpenLevelsCCScreenPacket(cultureId, buildingId, levelNumber);
        packet.saveTag(player);
        return packet;
    }

    public static S2COpenLevelsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        int levelNumber = buf.readInt();
        return new S2COpenLevelsCCScreenPacket(cultureId, buildingId, levelNumber);
    }

    public static S2COpenLevelsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        return S2COpenLevelsCCScreenPacket.create(null, cultureId, buildingId);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeInt(levelNumber);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
        tag.putString("building_id", buildingId);
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
            if (COMMON.isClientSide()) {
                ClientUtils.openLevelsCCScreen(packet);
            }
        }
    }
}