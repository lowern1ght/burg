package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.BUILDINGS_FOLDER_NAME;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenBuildingsCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_buildings_screen_cc");

    private final String cultureId;
    private final List<String> buildingIds;

    private S2COpenBuildingsCCScreenPacket(String cultureId, List<String> buildingIds){
        super(ID.getPath());
        this.cultureId = cultureId;
        this.buildingIds = buildingIds;
    }

    public static S2COpenBuildingsCCScreenPacket create(Player player, String cultureId){
        Path targetDir = Ouat.COMMON.getConfigFolder().toPath()
                .resolve(MOD_ID)
                .resolve(CULTURES_FOLDER_NAME)
                .resolve(cultureId)
                .resolve(BUILDINGS_FOLDER_NAME);
        List<String> buildingIds = new ArrayList<>();
        try {
            Files.createDirectories(targetDir);
            try (Stream<Path> stream = Files.list(targetDir)) {
                buildingIds = stream
                        .filter(path -> path.toString().endsWith(".json"))
                        .map(path -> path.getFileName().toString().substring(0, path.getFileName().toString().length()  - 5))
                        .toList();
            }
        } catch (IOException e) {
            if(player instanceof ServerPlayer serverPlayer){
                Ouat.clientChat(serverPlayer, "cc", "error_buildings_folder");
            }
            Ouat.debug("An error occurred while reading a culture file : " + e);
        }
        S2COpenBuildingsCCScreenPacket packet = new S2COpenBuildingsCCScreenPacket(cultureId, buildingIds);
        packet.saveTag(player);
        return packet;
    }

    public static S2COpenBuildingsCCScreenPacket decode(FriendlyByteBuf buf) {
        String cultureId = buf.readUtf();
        List<String> buildingIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenBuildingsCCScreenPacket(cultureId, buildingIds);
    }

    public static S2COpenBuildingsCCScreenPacket decode(CompoundTag tag) {
        String cultureId = tag.getString("culture_id");
        return S2COpenBuildingsCCScreenPacket.create(null, cultureId);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeCollection(buildingIds, FriendlyByteBuf::writeUtf);
    }

    public void encode(CompoundTag tag) {
        tag.putString("culture_id", cultureId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public List<String> getBuildingIds() {
        return buildingIds;
    }

    public String getCultureId() {
        return cultureId;
    }

    public static class Handler {
        public static void handle(S2COpenBuildingsCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openBuildingsCCScreen(packet);
            }
        }
    }
}