package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;
import oshi.util.tuples.Pair;

import java.nio.file.Path;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public record C2SSaveLevelCCPacket(String cultureId, String buildingId, String level, String requiredEra, String dwellingSlots, List<Pair<String, String>> professionSlots) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_save_level_cc");

    public static C2SSaveLevelCCPacket decode(FriendlyByteBuf buf) {
        return new C2SSaveLevelCCPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readList((b) -> new Pair<>(b.readUtf(), b.readUtf())));
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(level);
        buf.writeUtf(requiredEra);
        buf.writeUtf(dwellingSlots);
        buf.writeCollection(professionSlots, (b, pair) -> {
            b.writeUtf(pair.getA());
            b.writeUtf(pair.getB());
        });
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
        int levelInt = Integer.parseInt(level);
        // If there isn't enough levels already created, we add blanks level in between.
        for (int i = 0; i <= levelInt - data.levels.size(); i++) {
            data.levels.add(new BuildingDataHandler.BuildingLevelsHandler(new JsonObject()));
        }
        BuildingDataHandler.BuildingLevelsHandler levelData = data.levels.get(levelInt);
        levelData.requiredEra.set(requiredEra);
        levelData.dwellingSlots.set(dwellingSlots);
        levelData.workingSlots.clear();
        professionSlots.forEach(pair -> {
            BuildingDataHandler.WorkingSlotHandler slot = new BuildingDataHandler.WorkingSlotHandler(new JsonObject());
            slot.id.set(pair.getA());
            slot.maxLevel.set(pair.getB());
            levelData.workingSlots.add(slot);
        });
        data.saveJson(jsonPath, player, cultureId);
    }
}
