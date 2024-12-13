package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.client.screen.TownMapItemScreen;
import org.dawnoftime.onceuponatown.network.IOuatPacket;
import org.dawnoftime.onceuponatown.network.S2COpenTownMapScreenPacket;

import java.util.List;

import static org.dawnoftime.onceuponatown.Ouat.createOuatResource;

public record S2COpenScreenCCCultureListPacket(List<String> cultureIds) implements IOuatPacket {
    private static final ResourceLocation ID = createOuatResource("s2c_open_screen_cc_culture_list");

    public static S2COpenScreenCCCultureListPacket create(){
        return null;//new S2COpenScreenCCCultureListPacket();
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
                //Minecraft.getInstance().setScreen(new TownMapItemScreen(packet.tag()));
            });
        }
    }
}