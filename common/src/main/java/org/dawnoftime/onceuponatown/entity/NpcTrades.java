package org.dawnoftime.onceuponatown.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.trade.NpcOffer;

import java.util.ArrayList;
import java.util.List;

public class NpcTrades {
    public static List<NpcOffer> getOffers() {
        List<NpcOffer> offers = new ArrayList<>();
        // Buy deals
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.WHITE_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_GRAY_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GRAY_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLACK_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BROWN_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.RED_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.ORANGE_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.YELLOW_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIME_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GREEN_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.CYAN_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_BLUE_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLUE_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PURPLE_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.MAGENTA_WOOL, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PINK_WOOL, 1)).build());

        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.WHITE_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_GRAY_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GRAY_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLACK_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BROWN_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.RED_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.ORANGE_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.YELLOW_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIME_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GREEN_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.CYAN_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_BLUE_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLUE_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PURPLE_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.MAGENTA_BED, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PINK_BED, 1)).build());

        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.WHITE_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_GRAY_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GRAY_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLACK_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BROWN_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.RED_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.ORANGE_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.YELLOW_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIME_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GREEN_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.CYAN_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.LIGHT_BLUE_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.BLUE_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PURPLE_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.MAGENTA_BANNER, 1)).build());
        offers.add(NpcOffer.Builder.buyOffer(new ItemStack(Items.EMERALD, 1), new ItemStack(Items.PINK_BANNER, 1)).build());

        // Sell deals
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.WHITE_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_GRAY_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GRAY_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLACK_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BROWN_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.RED_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.ORANGE_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.YELLOW_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIME_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GREEN_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.CYAN_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_BLUE_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLUE_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PURPLE_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.MAGENTA_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PINK_CANDLE, 4), new ItemStack(Items.EMERALD, 1)).build());

        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.WHITE_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GRAY_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLACK_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BROWN_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.RED_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.ORANGE_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.YELLOW_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIME_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GREEN_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.CYAN_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLUE_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PURPLE_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.MAGENTA_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PINK_STAINED_GLASS, 6), new ItemStack(Items.EMERALD, 1)).build());

        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.WHITE_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_GRAY_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GRAY_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLACK_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BROWN_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.RED_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.ORANGE_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.YELLOW_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIME_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.GREEN_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.CYAN_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.LIGHT_BLUE_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.BLUE_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PURPLE_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.MAGENTA_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());
        offers.add(NpcOffer.Builder.sellOffer(new ItemStack(Items.PINK_STAINED_GLASS_PANE, 8), new ItemStack(Items.EMERALD, 1)).build());

        return offers;
    }
}
