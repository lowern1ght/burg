package org.dawnoftime.onceuponatown.network.culturecreator;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.OuatPacket;

import static org.dawnoftime.onceuponatown.Ouat.modResource;

public record C2SRequestLevelsCCPacket(String cultureId, String buildingId) implements OuatPacket {
    public static final ResourceLocation ID = modResource("c2s_request_levels_cc");

    public static C2SRequestLevelsCCPacket decode(FriendlyByteBuf buf) {
        return new C2SRequestLevelsCCPacket(buf.readUtf(), buf.readUtf());
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(cultureId);
        buf.writeUtf(buildingId);
    }

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    public void handle(MinecraftServer server, ServerPlayer player) {
        Ouat.COMMON.sendToClient(player, S2COpenLevelsCCScreenPacket.create(player, cultureId, buildingId));
    }
}
