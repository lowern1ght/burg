package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Quest;
import org.dawnoftime.onceuponatown.town.Town;

// Sent when the player places items into a DELIVERY condition slot on the quest card.
// The server removes items from the player (cursor first, then inventory) and updates received.
public record C2SQuestDeliverPacket(BlockPos anchorPos, String questId, int conditionIndex, int requestedAmount) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_quest_deliver");

    public static C2SQuestDeliverPacket decode(FriendlyByteBuf buf) {
        return new C2SQuestDeliverPacket(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeUtf(questId, 64);
        buf.writeVarInt(conditionIndex);
        buf.writeVarInt(requestedAmount);
    }

    public static class Handler {
        public static void handle(C2SQuestDeliverPacket packet, ServerPlayer player) {
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

            int idx = packet.conditionIndex();
            if (idx < 0 || idx >= quest.conditions.size()) return;
            Quest.Condition cond = quest.conditions.get(idx);
            if (!"DELIVERY".equals(cond.type) || cond.item == null) return;
            if (cond.received >= cond.required) return;

            int canAccept = cond.required - cond.received;
            int requested = Math.min(packet.requestedAmount(), canAccept);
            if (requested <= 0) return;

            // Remove from cursor first, then from inventory
            int toRemove = requested;
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && carried.getItem() == cond.item) {
                int fromCarried = Math.min(toRemove, carried.getCount());
                carried.shrink(fromCarried);
                toRemove -= fromCarried;
                if (carried.isEmpty()) {
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < player.getInventory().getContainerSize() && toRemove > 0; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!s.isEmpty() && s.getItem() == cond.item) {
                    int r = Math.min(toRemove, s.getCount());
                    s.shrink(r);
                    toRemove -= r;
                    if (s.isEmpty()) player.getInventory().setItem(i, ItemStack.EMPTY);
                }
            }

            int actualDelivered = requested - toRemove;
            if (actualDelivered <= 0) return;

            cond.received += actualDelivered;

            if (cond.sendToStock) {
                town.tryAddToStockUnchecked(cond.item, actualDelivered);
            }

            LevelTowns.get(level).markDirty();
            NetworkHelper.pushQuestUpdateToWatchers(level, town, packet.anchorPos());
            if (cond.sendToStock) {
                NetworkHelper.pushStockToWatchers(level, town, packet.anchorPos());
            }
        }
    }
}
