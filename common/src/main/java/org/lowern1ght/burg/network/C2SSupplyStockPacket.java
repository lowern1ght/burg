package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.blockentity.TownAnchorBlockEntity;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.Town;

// Sent by the SUPPLY-mode TownHubScreenV2 when the player supplies an item
// to a town. The packet carries one (itemId, quantity) pair; the server
// resolves the ItemId against the registry and merges the quantity into
// the town's reserve stock. ADR-0013 — the additive write path uses
// reserveStock.merge directly because Town.applyStockLedger is a
// full-replace of the reserve (overkill for a single-item supply) and
// the StockLedger cache rebuilds itself lazily on the next read.
public record C2SSupplyStockPacket(BlockPos anchorPos, String itemId, int quantity) implements CustomPacketPayload {

    public static final Type<C2SSupplyStockPacket> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "c2s_supply_stock"));

    public static final StreamCodec<FriendlyByteBuf, C2SSupplyStockPacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        C2SSupplyStockPacket::anchorPos,
        ByteBufCodecs.STRING_UTF8,
        C2SSupplyStockPacket::itemId,
        ByteBufCodecs.VAR_INT,
        C2SSupplyStockPacket::quantity,
        C2SSupplyStockPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SSupplyStockPacket packet, IPayloadContext context) {
        ServerPlayer player = (ServerPlayer) context.player();
        context.enqueueWork(() -> {
            if (packet.quantity() <= 0) return;
            if (packet.itemId() == null || packet.itemId().isEmpty()) return;

            ServerLevel level = (ServerLevel) player.level();
            if (!(level.getBlockEntity(packet.anchorPos()) instanceof TownAnchorBlockEntity)) return;

            Town town = LevelTowns.get(level).getTownAt(packet.anchorPos()).orElse(null);
            if (town == null) return;

            ResourceLocation rl = ResourceLocation.tryParse(packet.itemId());
            if (rl == null) return;
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null || item == Items.AIR) return;

            town.getReserveStock().merge(item, packet.quantity(), Integer::sum);
            LevelTowns.get(level).markDirty();
            NetworkHelper.pushStockToWatchers(level, town, packet.anchorPos());
        });
    }
}