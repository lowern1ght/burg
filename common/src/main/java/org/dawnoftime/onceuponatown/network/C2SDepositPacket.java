package org.dawnoftime.onceuponatown.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.blockentity.TownAnchorBlockEntity;
import org.dawnoftime.onceuponatown.screen.TownHubMenu;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Set;

public record C2SDepositPacket(BlockPos anchorPos) {
    public static final ResourceLocation ID = Ouat.modResource("c2s_deposit");

    public static C2SDepositPacket decode(FriendlyByteBuf buf) {
        return new C2SDepositPacket(buf.readBlockPos());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(anchorPos);
    }

    public static class Handler {
        public static void handle(C2SDepositPacket packet, ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            if (!(player.containerMenu instanceof TownHubMenu menu)) return;

            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            // Extended set includes quest items; base set is production items only (for routing)
            Set<Item> acceptedWithQuests = town.buildAcceptedItemSetWithQuests();
            Set<Item> productionItems    = town.buildAcceptedItemSet();
            SimpleContainer deposit = menu.getDepositContainer();

            boolean changed = false;
            for (int i = 0; i < deposit.getContainerSize(); i++) {
                ItemStack stack = deposit.getItem(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                // Items not accepted by the village (no quest, not produced) stay in the slot
                if (!acceptedWithQuests.contains(item)) continue;

                changed = true;
                int remaining = stack.getCount();

                // Step 1: consume units needed by active DELIVERY quests
                remaining -= town.applyToDeliveryQuests(item, remaining);

                // Step 2: route remainder to village stock if this item is produced by the village
                if (remaining > 0 && productionItems.contains(item)) {
                    town.tryAddToStockUnchecked(item, remaining);
                }
                // Step 3: any remaining amount is absorbed (consumed but not stored)

                deposit.setItem(i, ItemStack.EMPTY);
            }

            if (changed) {
                LevelTowns.get(level).markDirty();
                NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
            }
        }
    }
}
