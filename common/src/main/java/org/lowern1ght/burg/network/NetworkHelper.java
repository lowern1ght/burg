package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.lowern1ght.burg.screen.TownHubMenu;
import org.lowern1ght.burg.town.Town;
import org.lowern1ght.burg.town.TownLogEntry;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class NetworkHelper {
    // S2C delegates (set by each platform server-side init)
    public static BiConsumer<ServerPlayer, CompoundTag> sendTownHubPacket       = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingDefsPacket  = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendStockUpdatePacket   = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendBuildingListPacket  = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendQuestUpdatePacket   = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendEraUpdatePacket     = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendCitizenUpdatePacket = (player, data) -> {};
    public static BiConsumer<ServerPlayer, CompoundTag> sendLogEntryPacket     = (player, data) -> {};
    // ADR-0022 — acts 0–3 stay on the legacy menu flow; act-4 SUPPLY-mode
    // opens TownHubScreenV2 directly via this gateway. The server sends it
    // instead of the legacy sendTownHubPacket + openMenu pair when the town's
    // hubMode() == SUPPLY. The client reads it from TownHubClientState and
    // calls Minecraft.getInstance().setScreen(new TownHubScreenV2(...)).
    public static BiConsumer<ServerPlayer, BlockPos> sendOpenTownHubV2Packet = (player, anchorPos) -> {};

    /**
     * Publishes "this villager is ours" to everyone who can see it.
     *
     * <p>Takes the entity rather than a UUID because the loader resolves the audience from
     * it — {@code PacketDistributor.sendToPlayersTrackingEntity}. Called whenever membership
     * changes, which is the only time the client's copy can go stale.
     */
    public static BiConsumer<net.minecraft.world.entity.Entity, Boolean> broadcastVillagerIdentity =
        (villager, member) -> {};

    /**
     * Same fact, to one player who has just started tracking the villager.
     *
     * <p>Needed as well as the broadcast: a player walking into a town that was enlisted long
     * ago missed the broadcast entirely, and would see its citizens as strangers.
     */
    public static VillagerIdentitySender sendVillagerIdentity = (to, villager, member) -> {};

    public interface VillagerIdentitySender {
        void send(ServerPlayer to, UUID villager, boolean member);
    }

    // C2S delegates (set by each platform client-side init)
    public static Consumer<BlockPos>            sendToggleChatBroadcastPacket  = pos              -> {};
    public static BiConsumer<BlockPos, String>  sendQueueBuildingPacket        = (pos, defId)     -> {};
    public static BiConsumer<BlockPos, Integer> sendRemoveQueuedBuildingPacket = (pos, index)     -> {};
    public static BiConsumer<BlockPos, Long>    sendUpgradeBuildingPacket      = (pos, worldPos)  -> {};
    public static BiConsumer<BlockPos, String>  sendAdvanceEraPacket           = (pos, pathId)    -> {};
    public static Consumer<BlockPos>            sendDepositPacket              = pos              -> {};
    // ADR-0029 — second arg is the quest defId (the engine primary key),
    // forwarded to C2SContributeQuestPacket. The legacy per-spawn questId
    // is no longer what the wire carries.
    public static BiConsumer<BlockPos, String>  sendContributeQuestPacket      = (pos, defId)     -> {};
    public static Consumer<BlockPos>            sendRequestStockPacket         = pos              -> {};
    // Carries requested items for BUY mode: List<(itemId, count)> encoded via C2SBuyPacket
    public static BiConsumer<BlockPos, List<C2SBuyPacket.Entry>> sendBuyPacket = (pos, items) -> {};
    // SUPPLY-mode TownHubScreenV2 — supply one itemId/quantity to the town's reserve.
    // Plumbed through a three-arg helper because C2SSupplyStockPacket is the one
    // C2S payload that needs (anchor, itemId, quantity) — three independent fields,
    // not a list-of-pairs and not (anchor, scalar).
    public static SupplyStockSender sendSupplyStockPacket = (pos, itemId, quantity) -> {};

    /**
     * Wire-format adapter for {@link #sendSupplyStockPacket}. Carries
     * (anchorPos, itemId, quantity) without forcing the call site to
     * allocate the {@link C2SSupplyStockPacket} itself.
     */
    public interface SupplyStockSender {
        void send(BlockPos anchorPos, String itemId, int quantity);
    }

    // Sends a targeted stock update to every watcher.
    public static void pushStockToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getStockUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendStockUpdatePacket.accept(w, data);
    }

    // Sends a targeted building list update (map + queue + upgrades) to every watcher.
    public static void pushBuildingListToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getBuildingListData(anchorPos);
        for (ServerPlayer w : watchers) sendBuildingListPacket.accept(w, data);
    }

    // Sends a quest update to every player watching this town's hub (mirrors all other push methods).
    public static void pushQuestUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getQuestUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendQuestUpdatePacket.accept(w, data);
    }

    // Sends a targeted era update to every watcher.
    public static void pushEraUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getEraUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendEraUpdatePacket.accept(w, data);
    }

    // Sends a targeted citizen update to every watcher.
    public static void pushCitizenUpdateToWatchers(ServerLevel level, Town town, BlockPos anchorPos) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (watchers.isEmpty()) return;
        CompoundTag data = town.getCitizenUpdateData(anchorPos);
        for (ServerPlayer w : watchers) sendCitizenUpdatePacket.accept(w, data);
    }

    // Sends a log entry to every player watching this town's hub, and sends a
    // chat message to subscribed players who do not currently have the hub open.
    public static void pushLogEntryToWatchers(ServerLevel level, Town town, BlockPos anchorPos, TownLogEntry entry) {
        if (anchorPos == null) return;
        List<ServerPlayer> watchers = getWatchers(level, anchorPos);
        if (!watchers.isEmpty()) {
            CompoundTag data = new CompoundTag();
            data.putLong("AnchorPos", anchorPos.asLong());
            data.putString("Type", entry.type().name());
            data.putString("Param", entry.param());
            data.putLong("Tick", entry.gameTick());
            for (ServerPlayer w : watchers) sendLogEntryPacket.accept(w, data);
        }

        Set<UUID> subscribers = town.getChatSubscribers();
        if (!subscribers.isEmpty()) {
            Set<UUID> watcherIds = watchers.stream().map(ServerPlayer::getUUID).collect(Collectors.toSet());
            Component chatMsg = formatLogEntryForChat(entry);
            for (ServerPlayer player : level.players()) {
                if (subscribers.contains(player.getUUID()) && !watcherIds.contains(player.getUUID())) {
                    player.sendSystemMessage(chatMsg);
                }
            }
        }
    }

    private static Component formatLogEntryForChat(TownLogEntry entry) {
        MutableComponent prefix = Component.translatable("burg.message.village.prefix")
            .withStyle(s -> s.withColor(0xFFAA00));
        String param = entry.param();
        int amount = parseIntSafe(param);
        MutableComponent body = switch (entry.type()) {
            case BUILD_START   -> Component.translatable("burg.message.village.build_start",
                Component.translatable("burg.building." + param));
            case BUILD_DONE    -> Component.translatable("burg.message.village.build_done",
                Component.translatable("burg.building." + param));
            case UPGRADE_START -> Component.translatable("burg.message.village.upgrade_start",
                Component.translatable("burg.building." + param));
            case UPGRADE_DONE  -> Component.translatable("burg.message.village.upgrade_done",
                Component.translatable("burg.building." + param));
            case FOOD_CONSUMED -> Component.translatable("burg.message.village.food_consumed", amount);
            case VILLAGE_FULL  -> Component.translatable("burg.message.village.full");
        };
        int color = switch (entry.type()) {
            case BUILD_START, UPGRADE_START -> 0xAAAAFF;
            case BUILD_DONE, UPGRADE_DONE   -> 0x55FF55;
            case FOOD_CONSUMED              -> 0xDDDDDD;
            case VILLAGE_FULL               -> 0xFF5555;
        };
        return prefix.append(body.withStyle(s -> s.withColor(color)));
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static List<ServerPlayer> getWatchers(ServerLevel level, BlockPos anchorPos) {
        return level.players().stream()
            .filter(p -> p.containerMenu instanceof TownHubMenu m && anchorPos.equals(m.getAnchorPos()))
            .toList();
    }
}
