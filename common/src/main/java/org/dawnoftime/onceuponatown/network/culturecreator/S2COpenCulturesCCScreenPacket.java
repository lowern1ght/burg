package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.CulturesCCScreen;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.createOuatResource;

public class S2COpenCulturesCCScreenPacket implements IOuatPacket {
    private static final ResourceLocation ID = createOuatResource("s2c_open_screen_cc_culture_list");

    private final List<String> cultureIds;
    private S2COpenCulturesCCScreenPacket(List<String> cultureIds){
        this.cultureIds = cultureIds;
    }

    public static S2COpenCulturesCCScreenPacket create(){
        File targetDir = new File(Ouat.COMMON.getConfigFolder(), MOD_ID);
        List<String> cultures = new ArrayList<>();
        if (targetDir.exists() && targetDir.isDirectory()) {
            File[] files = targetDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        cultures.add(file.getName());
                    }
                }
            }
        }
        return new S2COpenCulturesCCScreenPacket(cultures);
    }

    public static S2COpenCulturesCCScreenPacket decode(FriendlyByteBuf buf) {
        List<String> cultureIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenCulturesCCScreenPacket(cultureIds);
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeCollection(cultureIds, FriendlyByteBuf::writeUtf);
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