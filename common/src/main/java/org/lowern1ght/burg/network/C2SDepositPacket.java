package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.blockentity.TownAnchorBlockEntity;
import org.lowern1ght.burg.datapack.TradePriceDataHandler;
import org.lowern1ght.burg.screen.TownHubMenu;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

import java.util.Set;

// TODO(ADR-0019 hub-becomes-window follow-up): this packet stays on the
// direct `Town.tryAddToStockUnchecked` path this PR per ADR-0018 §"diffuse
// rewiring to hub-becomes-window" — the act-4 player-facing deposit path
// migrates to `new SupplyStock.Handler(adapter).handle(...)` when the
// SUPPLY-mode widget lands in TownHubScreen. Move it then; don't
// half-do it here, the legacy reserve map and the StockLedger cache
// share a callback the current `applyStockLedger` does not know about.
public record C2SDepositPacket(BlockPos anchorPos) implements CustomPacketPayload {

    public static final Type<C2SDepositPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_deposit"));

    public static final StreamCodec<FriendlyByteBuf, C2SDepositPacket> STREAM_CODEC =
        StreamCodec.of(C2SDepositPacket::write, C2SDepositPacket::read);

    private static C2SDepositPacket read(FriendlyByteBuf buf) {
        return new C2SDepositPacket(buf.readBlockPos());
    }

    private static void write(FriendlyByteBuf buf, C2SDepositPacket packet) {
        buf.writeBlockPos(packet.anchorPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SDepositPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;
            if (!(player.containerMenu instanceof TownHubMenu menu)) return;

            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;
            Set<Item> productionItems = town.buildAcceptedItemSet();
            SimpleContainer deposit = menu.getDepositContainer();

            boolean changed = false;
            int totalEmeralds = 0;
            for (int i = 0; i < deposit.getContainerSize(); i++) {
                ItemStack stack = deposit.getItem(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                if (!productionItems.contains(item)) continue;

                changed = true;
                int count = stack.getCount();

                int sellPrice = TradePriceDataHandler.getSellPrice(item);
                int quantity  = TradePriceDataHandler.getQuantity(item);
                if (sellPrice > 0) totalEmeralds += sellPrice * (count / quantity);

                town.tryAddToStockUnchecked(item, count);
                deposit.setItem(i, ItemStack.EMPTY);
            }

            if (changed) {
                // Pay out emeralds in stacks of 64 so inventory handles it correctly
                int emeraldsLeft = totalEmeralds;
                while (emeraldsLeft > 0) {
                    int batch = Math.min(emeraldsLeft, 64);
                    ItemStack reward = new ItemStack(Items.EMERALD, batch);
                    if (!player.getInventory().add(reward)) player.drop(reward, false);
                    emeraldsLeft -= batch;
                }

                LevelTowns.get(level).markDirty();
                NetworkHelper.sendStockUpdatePacket.accept(player, town.getStockUpdateData(packet.anchorPos()));
            }
        });
    }
}
