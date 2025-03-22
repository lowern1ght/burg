package org.dawnoftime.onceuponatown.network.culturecreator;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culture_creator.VariantsCCScreen;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.modResource;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenVariantsCCScreenPacket implements OuatPacket {
    private static final ResourceLocation ID = modResource("s2c_open_variants_screen_cc");

    private final String cultureId;
    private final String buildingId;
    private final List<String> variantIds;

    private S2COpenVariantsCCScreenPacket(String cultureId, String buildingId, List<String> variantIds){
        this.cultureId = cultureId;
        this.buildingId = buildingId;
        this.variantIds = variantIds;
    }

    public static S2COpenVariantsCCScreenPacket create(Player player, String cultureId, String buildingId){
        List<String> variants;
        try {
            Path jsonPath = Ouat.COMMON.getConfigFolder().toPath()
                    .resolve(MOD_ID)
                    .resolve(CULTURES_FOLDER_NAME)
                    .resolve(cultureId)
                    .resolve(BUILDINGS_FOLDER_NAME)
                    .resolve(buildingId + ".json");
            String jsonContent = Files.readString(jsonPath);
            BuildingDataHandler data = new BuildingDataHandler(JsonParser.parseString(jsonContent).getAsJsonObject());
            variants = data.variants.stream().map(variant -> variant.name.get()).filter(Objects::nonNull).toList();
        } catch (IOException ignored) {
            variants = new ArrayList<>();
        }
        return new S2COpenVariantsCCScreenPacket(cultureId, buildingId, variants);
    }

    public static S2COpenVariantsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        String buildingId = buf.readUtf();
        List<String> variantIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenVariantsCCScreenPacket(cultureId, buildingId, variantIds);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
        buf.writeCollection(variantIds, FriendlyByteBuf::writeUtf);
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
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().execute(() -> Minecraft.getInstance().setScreen(new VariantsCCScreen(packet)));
            });
        }
    }
}