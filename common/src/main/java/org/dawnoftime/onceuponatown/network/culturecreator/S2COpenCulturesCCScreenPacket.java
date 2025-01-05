package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.CulturesCCScreen;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.*;

public class S2COpenCulturesCCScreenPacket implements IOuatPacket {
    private static final ResourceLocation ID = modResource("s2c_open_cultures_screen_cc");

    private final List<String> cultureIds;

    private S2COpenCulturesCCScreenPacket(List<String> cultureIds) {
        this.cultureIds = cultureIds;
    }

    public static S2COpenCulturesCCScreenPacket create(Player player){
        Path targetDir = Ouat.COMMON.getConfigFolder().toPath().resolve(MOD_ID);
        List<String> cultureIds = new ArrayList<>();
        if (Files.isDirectory(targetDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, Files::isDirectory)) {
                for (Path subDir : stream) {
                    cultureIds.add(subDir.getFileName().toString());
                }
            }catch (IOException e) {
                Ouat.clientChat(player, "cc", "error_cultures_folder");
                Ouat.debug("An error occurred while reading a culture file : " + e);
            }
        }
        return new S2COpenCulturesCCScreenPacket(cultureIds);
    }

    public static S2COpenCulturesCCScreenPacket decode(FriendlyByteBuf buf) {
        List<String> cultureIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenCulturesCCScreenPacket(cultureIds);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(cultureIds, FriendlyByteBuf::writeUtf);
    }

    public List<String> getCultureIds() {
        return cultureIds;
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public static class Handler {
        public static void handle(S2COpenCulturesCCScreenPacket packet) {
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().setScreen(new CulturesCCScreen(packet));
            });
        }
    }
}