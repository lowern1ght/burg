package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.dawnoftime.onceuponatown.screen.VillageChestMenu;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NetworkHelper {
    // S2C delegates (set by each platform server-side init)
    public static BiConsumer<ServerPlayer, CompoundTag> sendTownScrollPacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendVillageHubPacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingDefsPacket = (player, data) -> {};

    // Sends a fresh hub packet to every player who currently has this town's VillageChestScreen open.
    // Serializes hubData once and replicates it to all watchers; no-op if nobody is watching.
    public static void pushHubToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = level.players().stream()
            .filter(p -> p.containerMenu instanceof VillageChestMenu m && anchorPos.equals(m.getAnchorPos()))
            .toList();
        if (watchers.isEmpty()) return;
        CompoundTag hubData = town.getHubData(anchorPos);
        for (ServerPlayer watcher : watchers) {
            sendVillageHubPacket.accept(watcher, hubData);
        }
    }

    // C2S delegates (set by each platform client-side init)
    public static BiConsumer<BlockPos, String> sendQueueBuildingPacket = (pos, defId) -> {};
    public static BiConsumer<BlockPos, Integer> sendRemoveQueuedBuildingPacket = (pos, index) -> {};
    public static BiConsumer<BlockPos, Long> sendUpgradeBuildingPacket = (pos, worldPosLong) -> {};
    public static BiConsumer<BlockPos, String> sendAdvanceEraPacket = (pos, pathId) -> {};
    public static Consumer<BlockPos> sendDepositPacket = pos -> {};
    public static BiConsumer<BlockPos, String> sendClaimQuestPacket = (pos, questId) -> {};
}
