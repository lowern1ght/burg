package org.dawnoftime.onceuponatown.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.apache.logging.log4j.util.TriConsumer;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.network.culturecreator.*;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PacketHandler {
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Ouat.MOD_ID, "channel"), () -> "0", "0"::equals, "0"::equals);

    public static void init() {
        int i = 0;

        // Client to Server packets
        CHANNEL.registerMessage(i++, C2SChangeNpcTabPacket.class, C2SChangeNpcTabPacket::encode, C2SChangeNpcTabPacket::decode, makeC2SHandler(C2SChangeNpcTabPacket::handle));
        CHANNEL.registerMessage(i++, C2STradePacket.class, C2STradePacket::encode, C2STradePacket::decode, makeC2SHandler(C2STradePacket::handle));

        CHANNEL.registerMessage(i++, C2SRequestCulturesCCPacket.class, C2SRequestCulturesCCPacket::encode, C2SRequestCulturesCCPacket::decode, makeC2SHandler(C2SRequestCulturesCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestCultureCCPacket.class, C2SRequestCultureCCPacket::encode, C2SRequestCultureCCPacket::decode, makeC2SHandler(C2SRequestCultureCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestBuildingsCCPacket.class, C2SRequestBuildingsCCPacket::encode, C2SRequestBuildingsCCPacket::decode, makeC2SHandler(C2SRequestBuildingsCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestBuildingCCPacket.class, C2SRequestBuildingCCPacket::encode, C2SRequestBuildingCCPacket::decode, makeC2SHandler(C2SRequestBuildingCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SSaveBuildingCCPacket.class, C2SSaveBuildingCCPacket::encode, C2SSaveBuildingCCPacket::decode, makeC2SHandler(C2SSaveBuildingCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestLevelsCCPacket.class, C2SRequestLevelsCCPacket::encode, C2SRequestLevelsCCPacket::decode, makeC2SHandler(C2SRequestLevelsCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SSaveLevelsCCPacket.class, C2SSaveLevelsCCPacket::encode, C2SSaveLevelsCCPacket::decode, makeC2SHandler(C2SSaveLevelsCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestLevelCCPacket.class, C2SRequestLevelCCPacket::encode, C2SRequestLevelCCPacket::decode, makeC2SHandler(C2SRequestLevelCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SSaveLevelCCPacket.class, C2SSaveLevelCCPacket::encode, C2SSaveLevelCCPacket::decode, makeC2SHandler(C2SSaveLevelCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestVariantsCCPacket.class, C2SRequestVariantsCCPacket::encode, C2SRequestVariantsCCPacket::decode, makeC2SHandler(C2SRequestVariantsCCPacket::handle));
        CHANNEL.registerMessage(i++, C2SRequestVariantLevelsCCPacket.class, C2SRequestVariantLevelsCCPacket::encode, C2SRequestVariantLevelsCCPacket::decode, makeC2SHandler(C2SRequestVariantLevelsCCPacket::handle));

        // Server to Client packets
        CHANNEL.registerMessage(i++, S2COpenTownMapScreenPacket.class, S2COpenTownMapScreenPacket::encode, S2COpenTownMapScreenPacket::decode, makeS2CHandler(S2COpenTownMapScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenCulturesCCScreenPacket.class, S2COpenCulturesCCScreenPacket::encode, S2COpenCulturesCCScreenPacket::decode, makeS2CHandler(S2COpenCulturesCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenCultureCCScreenPacket.class, S2COpenCultureCCScreenPacket::encode, S2COpenCultureCCScreenPacket::decode, makeS2CHandler(S2COpenCultureCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenBuildingsCCScreenPacket.class, S2COpenBuildingsCCScreenPacket::encode, S2COpenBuildingsCCScreenPacket::decode, makeS2CHandler(S2COpenBuildingsCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenBuildingCCScreenPacket.class, S2COpenBuildingCCScreenPacket::encode, S2COpenBuildingCCScreenPacket::decode, makeS2CHandler(S2COpenBuildingCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenLevelsCCScreenPacket.class, S2COpenLevelsCCScreenPacket::encode, S2COpenLevelsCCScreenPacket::decode, makeS2CHandler(S2COpenLevelsCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenLevelCCScreenPacket.class, S2COpenLevelCCScreenPacket::encode, S2COpenLevelCCScreenPacket::decode, makeS2CHandler(S2COpenLevelCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenVariantsCCScreenPacket.class, S2COpenVariantsCCScreenPacket::encode, S2COpenVariantsCCScreenPacket::decode, makeS2CHandler(S2COpenVariantsCCScreenPacket.Handler::handle));
        CHANNEL.registerMessage(i++, S2COpenVariantLevelsCCScreenPacket.class, S2COpenVariantLevelsCCScreenPacket::encode, S2COpenVariantLevelsCCScreenPacket::decode, makeS2CHandler(S2COpenVariantLevelsCCScreenPacket.Handler::handle));
    }

    private static <T> BiConsumer<T, Supplier<NetworkEvent.Context>> makeC2SHandler(TriConsumer<T, MinecraftServer, ServerPlayer> handler) {
        return (m, contextSupplier) -> {
            ServerPlayer player = contextSupplier.get().getSender();
            if (player != null) {
                handler.accept(m, player.getServer(), contextSupplier.get().getSender());
                contextSupplier.get().setPacketHandled(true);
            }
        };
    }

    private static <T> BiConsumer<T, Supplier<NetworkEvent.Context>> makeS2CHandler(Consumer<T> consumer) {
        return (m, contextSupplier) -> {
            consumer.accept(m);
            contextSupplier.get().setPacketHandled(true);
        };
    }
}
