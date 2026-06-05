package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.ClientUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.*;
import static org.dawnoftime.onceuponatown.datapack.core.DataHandler.CULTURES_FOLDER_NAME;

public class S2COpenCulturesCCScreenPacket extends OpenScreenPacket {
    private static final ResourceLocation ID = modResource("s2c_open_cultures_screen_cc");

    private final List<String> cultureIds;

    private S2COpenCulturesCCScreenPacket(List<String> cultureIds) {
        super(ID.getPath());
        this.cultureIds = cultureIds;
    }

    public static S2COpenCulturesCCScreenPacket create(Player player){
        Path targetDir = Ouat.COMMON.getConfigFolder().toPath()
                .resolve(MOD_ID)
                .resolve(CULTURES_FOLDER_NAME);
        List<String> cultureIds = new ArrayList<>();
        try {
            Files.createDirectories(targetDir);
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, Files::isDirectory)) {
                for (Path subDir : stream) {
                    cultureIds.add(subDir.getFileName().toString());
                }
            }
        } catch (IOException e) {
            if(player instanceof ServerPlayer serverPlayer){
                Ouat.clientChat(serverPlayer, "cc", "error_cultures_folder");
            }
            Ouat.debug("An error occurred while reading a culture file : " + e);
        }
        S2COpenCulturesCCScreenPacket packet = new S2COpenCulturesCCScreenPacket(cultureIds);
        packet.saveTag(player);
        return packet;
    }

    public static S2COpenCulturesCCScreenPacket decode(FriendlyByteBuf buf) {
        List<String> cultureIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenCulturesCCScreenPacket(cultureIds);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(cultureIds, FriendlyByteBuf::writeUtf);
    }

    public void encode(CompoundTag tag) {}

    public List<String> getCultureIds() {
        return cultureIds;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenCulturesCCScreenPacket packet) {
            if (COMMON.isClientSide()) {
                ClientUtils.openCulturesCCScreen(packet);
            }
        }
    }
}