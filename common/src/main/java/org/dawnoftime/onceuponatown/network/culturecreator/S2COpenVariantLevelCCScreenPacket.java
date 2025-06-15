package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;

import java.nio.file.Path;
import java.util.Optional;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenVariantLevelCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variant_level_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final String variantId;
    private final int level;
    private final String sizeX;
    private final String sizeY;
    private final String sizeZ;

    private S2COpenVariantLevelCCScreenPacket(String cultureId, String buildingId, String variantId, int level, String sizeX, String sizeY, String sizeZ){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantId = variantId;
        this.level = level;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
    }

    public static S2COpenVariantLevelCCScreenPacket create(Player player, String cultureId, String buildingId, String variantId, int level){
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            BuildingDataHandler data = new BuildingDataHandler(DataHandler.loadJson(jsonPath));
            int numberOfLevels = data.levels.size();
            if (numberOfLevels <= level) {
                return null;
            }
            Optional<BuildingDataHandler.BuildingVariantHandler> variantOpt = data.variants.stream()
                    .filter(variant -> variant.name.asString().equals(variantId))
                    .findFirst();
            if (variantOpt.isEmpty()) {
                return null;
            }
            BuildingDataHandler.BuildingVariantHandler variant = variantOpt.get();
            S2COpenVariantLevelCCScreenPacket packet = new S2COpenVariantLevelCCScreenPacket(cultureId, buildingId, variantId, level, variant.sizeX.asString(), variant.sizeY.asString(), variant.sizeZ.asString());
            packet.saveTag(player);
            return packet;
        } catch (Exception ignored) {}
        return null;
    }

    public static S2COpenVariantLevelCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        String variantId = buf.readUtf();
        byte level = buf.readByte();
        String sizeX = buf.readUtf();
        String sizeY = buf.readUtf();
        String sizeZ = buf.readUtf();
        return new S2COpenVariantLevelCCScreenPacket(cultureId, buildingId, variantId, level, sizeX, sizeY, sizeZ);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeUtf(variantId);
        buf.writeByte(level);
        buf.writeUtf(sizeX);
        buf.writeUtf(sizeY);
        buf.writeUtf(sizeZ);
    }

    public static S2COpenVariantLevelCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        String level = tag.getString("variant_id");
        byte numberOfLevels = tag.getByte("level");
        return S2COpenVariantLevelCCScreenPacket.create(null, cultureId, buildingId, level, numberOfLevels);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
        tag.putString("building_id", buildingId);
        tag.putString("variant_id", variantId);
        tag.putInt("level", level);
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

    public String getVariantId() {
        return variantId;
    }

    public int getLevel() {
        return level;
    }

    public static class Handler {
        public static void handle(S2COpenVariantLevelCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openVariantLevelCCScreen(packet);
            }
        }
    }
}