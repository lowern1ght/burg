package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.*;

public class S2COpenVariantLevelsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variant_levels_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final String variantId;

    private S2COpenVariantLevelsCCScreenPacket(String cultureId, String buildingId, String variantId){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
    }

    @Nullable
    public static S2COpenVariantLevelsCCScreenPacket create(Player player, String cultureId, String buildingId, String variantId){
        try {
            Path jsonPath = COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            BuildingDataHandler data = new BuildingDataHandler(loadJson(jsonPath));
            BuildingDataHandler.BuildingVariantHandler variant = null;
            for (BuildingDataHandler.BuildingVariantHandler testedVariant : data.variants) {
                if (testedVariant.name.asString().equals(variantId)) {
                    variant = testedVariant;
                    break;
                }
            }

            S2COpenVariantLevelsCCScreenPacket packet = new S2COpenVariantLevelsCCScreenPacket(cultureId, buildingId, variantId);
            packet.saveTag(player);
            return packet;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static S2COpenVariantLevelsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        String level = buf.readUtf();
        return new S2COpenVariantLevelsCCScreenPacket(cultureId, buildingId, level);
    }

    public static S2COpenVariantLevelsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        String level = tag.getString("level");
        return S2COpenVariantLevelsCCScreenPacket.create(null, cultureId, buildingId, level);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
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

    public static class Handler {
        public static void handle(S2COpenVariantLevelsCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                //ClientUtils.openLevelCCScreen(packet);
            }
        }
    }
}