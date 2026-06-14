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

public record C2SClaimQuestPacket(BlockPos anchorPos, String questId) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_claim_quest");

    public static C2SClaimQuestPacket decode(FriendlyByteBuf buf) {
        return new C2SClaimQuestPacket(buf.readBlockPos(), buf.readUtf(64));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
        buf.writeUtf(questId, 64);
    }

    public static class Handler {
        public static void handle(C2SClaimQuestPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity anchor)) return;
            if (!(player.containerMenu instanceof TownHubMenu)) return;

            Town town = anchor.getTown();
            Quest quest = null;
            for (Quest q : town.getActiveQuests()) {
                if (q.questId.equals(packet.questId())) { quest = q; break; }
            }
            if (quest == null) return;

            if ("NOTE".equals(quest.questType)) {
                // NOTE dismissed by the player: mark def as permanently seen, no reward
                town.addDismissedNote(quest.defId);
                town.removeQuest(packet.questId());
            } else {
                if (!quest.allConditionsMet()) return;
                // Reward goes directly to the player's inventory
                if (quest.reward != null && "PLAYER".equals(quest.reward.type) && quest.reward.item != null) {
                    ItemStack rewardStack = new ItemStack(quest.reward.item, quest.reward.amount);
                    if (!player.getInventory().add(rewardStack)) {
                        player.drop(rewardStack, false);
                    }
                }
                town.removeQuest(packet.questId());
            }

            LevelTowns.get(level).markDirty();
            NetworkHelper.sendQuestUpdatePacket.accept(player, town.getQuestUpdateData(packet.anchorPos()));
        }
    }
}
