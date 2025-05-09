package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenLevelCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_level_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final String level;
    private final String requiredEra;
    private final String dwellingSlots;

    private S2COpenLevelCCScreenPacket(String cultureId, String buildingId, String level, String requiredEra, String dwellingSlots){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.level = level;
        this.requiredEra = requiredEra;
        this.dwellingSlots = dwellingSlots;
    }

    @Nullable
    public static S2COpenLevelCCScreenPacket create(Player player, String cultureId, String buildingId, String level){
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            BuildingDataHandler data = new BuildingDataHandler(DataHandler.loadJson(jsonPath));
            int levelInt = Integer.parseInt(level);
            BuildingDataHandler.BuildingLevelsHandler levelData = data.levels.size() > levelInt ? data.levels.get(Integer.parseInt(level)) : new BuildingDataHandler.BuildingLevelsHandler(new JsonObject());
            S2COpenLevelCCScreenPacket packet = new S2COpenLevelCCScreenPacket(cultureId, buildingId, level, levelData.requiredEra.asString(), levelData.dwellingSlots.asString());
            packet.saveTag(player);
            return packet;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static S2COpenLevelCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        String level = buf.readUtf();
        String requiredEra = buf.readUtf();
        String dwellingSlots = buf.readUtf();
        return new S2COpenLevelCCScreenPacket(cultureId, buildingId, level, requiredEra, dwellingSlots);
    }

    public static S2COpenLevelCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        String level = tag.getString("level");
        return S2COpenLevelCCScreenPacket.create(null, cultureId, buildingId, level);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(level);
        buf.writeUtf(requiredEra);
        buf.writeUtf(dwellingSlots);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
        tag.putString("building_id", buildingId);
        tag.putString("level", level);
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

    public String getLevel() {
        return level;
    }

    public String getDwellingSlots() {
        return dwellingSlots;
    }

    public String getRequiredEra() {
        return requiredEra;
    }

    public static class Handler {
        public static void handle(S2COpenLevelCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openLevelCCScreen(packet);
            }
        }
    }
}