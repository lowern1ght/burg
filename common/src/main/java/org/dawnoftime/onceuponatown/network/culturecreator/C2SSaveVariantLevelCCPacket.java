package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.nio.file.Path;
import java.util.Optional;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public record C2SSaveVariantLevelCCPacket(String cultureId, String buildingId, String variantId, int level, String x, String y, String z, byte cultureCreatorState) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_save_variant_level_cc");

    public static C2SSaveVariantLevelCCPacket decode(FriendlyByteBuf buf) {
        return new C2SSaveVariantLevelCCPacket(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readInt(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readByte());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(variantId);
        buf.writeInt(level);
        buf.writeUtf(x);
        buf.writeUtf(y);
        buf.writeUtf(z);
        buf.writeByte(cultureCreatorState);
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
        Optional<BuildingDataHandler.BuildingVariantHandler> variantOpt = data.variants.stream()
                .filter(variant -> variant.name.asString().equals(variantId))
                .findFirst();
        if (variantOpt.isEmpty()) {
            return;
        }
        BuildingDataHandler.BuildingVariantHandler variant = variantOpt.get();
        variant.sizeX.set(x);
        variant.sizeY.set(y);
        variant.sizeZ.set(z);
        data.saveJson(jsonPath, player, cultureId);
        CultureCreatorItem.setClientPlayerCCState(player, cultureCreatorState);
    }
}
