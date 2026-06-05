package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

public class NetworkHelper {
    // S2C delegates (set by each platform server-side init)
    public static BiConsumer<ServerPlayer, CompoundTag> sendTownScrollPacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendVillageHubPacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingDefsPacket = (player, data) -> {};

    // C2S delegates (set by each platform client-side init)
    public static BiConsumer<BlockPos, String> sendQueueBuildingPacket = (pos, defId) -> {};
    public static BiConsumer<BlockPos, Integer> sendRemoveQueuedBuildingPacket = (pos, index) -> {};
    public static BiConsumer<BlockPos, Long> sendUpgradeBuildingPacket = (pos, worldPosLong) -> {};
}
