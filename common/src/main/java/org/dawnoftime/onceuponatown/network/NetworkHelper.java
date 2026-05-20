package org.dawnoftime.onceuponatown.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public class NetworkHelper {
    // Set by each platform (Fabric/Forge) during mod initialization.
    public static BiConsumer<ServerPlayer, CompoundTag> sendTownScrollPacket = (player, data) -> {};
}
