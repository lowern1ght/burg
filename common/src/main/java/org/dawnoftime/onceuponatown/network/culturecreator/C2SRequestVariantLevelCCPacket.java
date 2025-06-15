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
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public record C2SRequestVariantLevelCCPacket(String cultureId, String buildingId, String variantId, int level) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_variant_level_cc");

    public static C2SRequestVariantLevelCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestVariantLevelCCPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(variantId);
        buf.writeInt(level);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            BuildingDataHandler data = new BuildingDataHandler(loadJson(jsonPath));
            int numberOfLevels = data.levels.size();
            if (numberOfLevels <= level) {
                throw new NoSuchElementException("Impossible to access a variant level higher than the total number of level of the Building.");
            }
            Optional<BuildingDataHandler.BuildingVariantHandler> variantOpt = data.variants.stream()
                    .filter(variant -> variant.name.asString().equals(variantId))
                    .findFirst();
            if (variantOpt.isEmpty()) {
                BuildingDataHandler.BuildingVariantHandler newVariant = new BuildingDataHandler.BuildingVariantHandler(new JsonObject());
                newVariant.name.set(variantId);
                data.variants.add(newVariant);
                data.resizeLevelLists(numberOfLevels);
                data.saveJson(jsonPath, player, cultureId);
                return;
            }
            BuildingDataHandler.BuildingVariantHandler variant = variantOpt.get();
            if (numberOfLevels != variant.levels.size()) {
                data.resizeLevelLists(numberOfLevels);
                data.saveJson(jsonPath, player, cultureId);
            }
            Ouat.COMMON.sendToClient(player, S2COpenVariantLevelCCScreenPacket.create(player, cultureId, buildingId, variantId, level));
            return;
        } catch (Exception e) {
            Ouat.clientChat(player, "cc", "culture_error", cultureId);
            Ouat.debug("An error occurred while reading a culture file of '" + cultureId + "' : " + e);
        }
        Ouat.COMMON.sendToClient(player, S2COpenCulturesCCScreenPacket.create(player));
    }
}
