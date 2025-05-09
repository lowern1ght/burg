package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenVariantsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variants_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final List<String> variantIds;

    private S2COpenVariantsCCScreenPacket(String cultureId, String buildingId, List<String> variantIds){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantIds = variantIds;
    }

    public static S2COpenVariantsCCScreenPacket create(Player player, String cultureId, String buildingId){
        List<String> variants;
        Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                .resolve(MOD_ID)
                .resolve(CULTURES_FOLDER_NAME)
                .resolve(cultureId)
                .resolve(BUILDINGS_FOLDER_NAME)
                .resolve(buildingId + ".json");
        BuildingDataHandler data = new BuildingDataHandler(DataHandler.loadJson(jsonPath));
        variants = data.variants.stream().map(variant -> variant.name.asString()).toList();
        S2COpenVariantsCCScreenPacket packet = new S2COpenVariantsCCScreenPacket(cultureId, buildingId, variants);
        packet.saveTag(player);
        return packet;
    }

    public static S2COpenVariantsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        List<String> variantIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenVariantsCCScreenPacket(cultureId, buildingId, variantIds);
    }

    public static S2COpenVariantsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        String buildingId = tag.getString("building_id");
        return S2COpenVariantsCCScreenPacket.create(null, cultureId, buildingId);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeCollection(variantIds, FriendlyByteBuf::writeUtf);
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

    public List<String> getVariantIds() {
        return variantIds;
    }

    public static class Handler {
        public static void handle(S2COpenVariantsCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openVariantsCCScreen(packet);
            }
        }
    }
}