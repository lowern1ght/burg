package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.IOuatPacket;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SRequestCultureCCPacket(String cultureId) implements IOuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_culture_cc");

    public static C2SRequestCultureCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestCultureCCPacket(buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        Ouat.COMMON.sendToClient(player, S2COpenCultureCCScreenPacket.create(cultureId));
    }
}
