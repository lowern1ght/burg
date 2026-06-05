package org.dawnoftime.onceuponatown.trade;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TradeUtils {
    public static void writeOffersToStream(List<NpcOffer> offers, FriendlyByteBuf buf) {
        buf.writeCollection(offers, (buffer, offer) -> {
            buffer.writeItem(offer.getInputA());
            buffer.writeItem(offer.getInputB());
            buffer.writeItem(offer.getResult());
            buffer.writeEnum(offer.getTradeType());
        });
    }

    public static List<NpcOffer> createOffersFromStream(FriendlyByteBuf buf) {
        return buf.readCollection(ArrayList::new, (buffer) -> {
            ItemStack inputA = buffer.readItem();
            ItemStack inputB = buffer.readItem();
            ItemStack result = buffer.readItem();
            NpcOffer.TradeType tradeType = buffer.readEnum(NpcOffer.TradeType.class);
            return new NpcOffer.Builder(tradeType, inputA, result)
                .inputB(inputB)
                .build();
        });
    }
}
