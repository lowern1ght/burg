package org.dawnoftime.onceuponatown.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.util.TriConsumer;
import org.dawnoftime.onceuponatown.Ouat;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PacketHandler {
    public static final SimpleChannel CHANNEL =  NetworkRegistry.newSimpleChannel(new ResourceLocation(Ouat.MOD_ID, "channel"), () -> "0", "0"::equals, "0"::equals);

    public static void init() {
        int i = 0;

        // Client to Server packets
        CHANNEL.registerMessage(i++, C2SSelectBuyDealPacket.class, C2SSelectBuyDealPacket::encode, C2SSelectBuyDealPacket::decode, makeC2SHandler(C2SSelectBuyDealPacket::handle));
        CHANNEL.registerMessage(i++, C2SSellScreenPacket.class, C2SSellScreenPacket::encode, C2SSellScreenPacket::decode, makeC2SHandler(C2SSellScreenPacket::handle));
        CHANNEL.registerMessage(i++, C2SChangeNpcTabPacket.class, C2SChangeNpcTabPacket::encode, C2SChangeNpcTabPacket::decode, makeC2SHandler(C2SChangeNpcTabPacket::handle));
        CHANNEL.registerMessage(i++, C2SSelectTradePacket.class, C2SSelectTradePacket::encode, C2SSelectTradePacket::decode, makeC2SHandler(C2SSelectTradePacket::handle));

        // Server to Client packets
        CHANNEL.registerMessage(i++, S2COpenTownMapScreenPacket.class, S2COpenTownMapScreenPacket::encode, S2COpenTownMapScreenPacket::decode, makeS2CHandler(S2COpenTownMapScreenPacket.Handler::handle));
    }

    private static <T> BiConsumer<T, Supplier<NetworkEvent.Context>> makeC2SHandler(TriConsumer<T, MinecraftServer, ServerPlayer> handler) {
        return (m, contextSupplier) -> {
            ServerPlayer player = contextSupplier.get().getSender();
            if(player != null){
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
