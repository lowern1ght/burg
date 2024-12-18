package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.screen.TownMapItemScreen;
import org.dawnoftime.onceuponatown.client.screen.culturecreator.CCBaseScreen;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.network.S2COpenTownMapScreenPacket;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;
import static org.dawnoftime.onceuponatown.Ouat.createOuatResource;

public record S2COpenScreenCCCultureListPacket(List<String> cultureIds) implements IOuatPacket {
    private static final ResourceLocation ID = createOuatResource("s2c_open_screen_cc_culture_list");

    public static S2COpenScreenCCCultureListPacket create(){
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
        return new S2COpenScreenCCCultureListPacket(cultures);
    }

    public static S2COpenScreenCCCultureListPacket decode(FriendlyByteBuf buf) {
        List<String> cultureIds = buf.readList(FriendlyByteBuf::readUtf);
        return new S2COpenScreenCCCultureListPacket(cultureIds);
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
        public static void handle(S2COpenScreenCCCultureListPacket packet) {
            Minecraft.getInstance().execute(() -> {
                //Minecraft.getInstance().setScreen(new CCBaseScreen(packet) {});
            });
        }
    }
}