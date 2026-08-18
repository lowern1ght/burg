package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.blockentity.TownAnchorBlockEntity;
import org.lowern1ght.burg.screen.TownHubMenu;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Quest;
import org.lowern1ght.burg.town.Town;

// Sent when the player clicks the Contribute button on a TASK quest.
// The server validates inventory, takes all required items, and grants the reward atomically.
public record C2SContributeQuestPacket(BlockPos anchorPos, String questId) implements CustomPacketPayload {

    public static final Type<C2SContributeQuestPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_contribute_quest"));

    public static final StreamCodec<FriendlyByteBuf, C2SContributeQuestPacket> STREAM_CODEC =
        StreamCodec.of(C2SContributeQuestPacket::write, C2SContributeQuestPacket::read);

    private static C2SContributeQuestPacket read(FriendlyByteBuf buf) {
        return new C2SContributeQuestPacket(buf.readBlockPos(), buf.readUtf(64));
    }

    private static void write(FriendlyByteBuf buf, C2SContributeQuestPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
        buf.writeUtf(packet.questId(), 64);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SContributeQuestPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            if (!(player.containerMenu instanceof TownHubMenu)) return;

            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;

            Quest quest = null;
            for (Quest q : town.getActiveQuests()) {
                if (q.questId.equals(packet.questId())) { quest = q; break; }
            }
            if (quest == null) return;

            // Validate player has all required items before taking anything
            for (Quest.Condition cond : quest.conditions) {
                if ("DELIVERY".equals(cond.type) && cond.item != null) {
                    if (countInInventory(player, cond.item) < cond.required) return;
                }
            }

            // Take items and optionally route to stock
            boolean stockUpdated = false;
            for (Quest.Condition cond : quest.conditions) {
                if (!"DELIVERY".equals(cond.type) || cond.item == null) continue;
                int toRemove = cond.required;
                for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                    ItemStack s = player.getInventory().getItem(i);
                    if (!s.isEmpty() && s.getItem() == cond.item) {
                        int r = Math.min(toRemove, s.getCount());
                        s.shrink(r);
                        toRemove -= r;
                        if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                    }
                }
                if (cond.sendToStock) {
                    town.tryAddToStockUnchecked(cond.item, cond.required);
                    stockUpdated = true;
                }
            }

            // Give reward directly to player inventory, drop if full
            if (quest.reward != null && "PLAYER".equals(quest.reward.type) && quest.reward.item != null) {
                ItemStack rewardStack = new ItemStack(quest.reward.item, quest.reward.amount);
                if (!player.getInventory().add(rewardStack)) {
                    player.drop(rewardStack, false);
                }
            }

            town.removeQuest(packet.questId());
            town.getQuestDefLastCompleted().put(quest.defId, level.getGameTime());
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, packet.anchorPos());
            if (stockUpdated) NetworkHelper.pushStockToWatchers(level, town, packet.anchorPos());
        });
    }

    private static int countInInventory(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() == item) count += s.getCount();
        }
        return count;
    }
}
