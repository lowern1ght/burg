package org.dawnoftime.onceuponatown.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TradeUtils {
    public static void writeNpcOffersToStream(List<NpcOffer> deals, FriendlyByteBuf friendlyByteBuf) {
        friendlyByteBuf.writeCollection(deals, (buf, deal) -> {
            buf.writeItem(deal.getInputA());
            buf.writeItem(deal.getInputB());
            buf.writeItem(deal.getResult());
            buf.writeEnum(deal.getTradeType());
        });
    }

    public static List<NpcOffer> createNpcOffersFromStream(FriendlyByteBuf friendlyByteBuf) {
        return friendlyByteBuf.readCollection(ArrayList::new, (buf) -> {
            ItemStack requiredA = buf.readItem();
            ItemStack requiredB = buf.readItem();
            ItemStack result = buf.readItem();
            NpcOffer.TradeType tradeType = buf.readEnum(NpcOffer.TradeType.class);
            return new NpcOffer.Builder(tradeType, requiredA, result).requiredB(requiredB).build();
        });
    }
}
