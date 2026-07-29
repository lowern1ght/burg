package org.dawnoftime.onceuponatown.entity;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.datapack.SettlerJobsDataHandler;
import org.dawnoftime.onceuponatown.datapack.TradePriceDataHandler;
import org.dawnoftime.onceuponatown.entity.ai.ActivityDef;
import org.dawnoftime.onceuponatown.people.Person;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.Currency;
import org.dawnoftime.onceuponatown.town.ProductionEntry;
import org.dawnoftime.onceuponatown.town.TransformationRecipe;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What a person will trade, built from the trade they actually hold.
 *
 * <p><b>Nothing here is a table of offers.</b> A carpenter sells what a carpenter's shop produces
 * and buys what it consumes, because those are already declared — {@code production} and
 * {@code transformations} on the building def. Writing a second list of goods per profession
 * would be a second source of truth about what a trade does, and this repo has paid for that
 * shape of mistake repeatedly. Add a building's production to its json and its worker starts
 * selling it.
 *
 * <p>Prices come from {@code trade_prices.json}, which the hub already uses, so a player cannot
 * find one price at the campfire and a different one from the person who made the thing. Read as
 * <b>pennies</b> now rather than emeralds — see {@link Currency} for why one denomination made
 * everything cheap cost the same.
 *
 * <p>Skill raises the <b>quantity</b> a person has to sell, not the price they charge. A master
 * charging more is the realistic reading and the worse game: it punishes the player for the
 * town's success, in a mod whose whole loop is helping the town succeed. More stock is the same
 * fact told the other way round.
 */
public final class Trading {

    /** Uses per offer before it is exhausted. Restocks with the trading day; see {@code Npc}. */
    private static final int BASE_USES = 6;

    /** Extra uses per level of skill, so a master simply has more to sell. */
    private static final int USES_PER_SKILL = 4;

    private Trading() {
    }

    /**
     * Everything this person will deal in, or an empty list for somebody with no trade.
     *
     * <p>An empty list is meaningful: {@code Npc.mobInteract} refuses to open a trade screen on
     * one, and says who the person is instead. A window with nothing in it reads as broken.
     */
    public static MerchantOffers offersFor(String trade, int skill) {
        MerchantOffers offers = new MerchantOffers();
        if (trade == null) return offers;

        int uses = BASE_USES + USES_PER_SKILL * Math.max(0, skill);

        // Every building this trade is worked at -- a trade can name more than one, and a person
        // holding it can deal in the goods of any of them.
        Set<String> buildings = new LinkedHashSet<>();
        for (ActivityDef job : SettlerJobsDataHandler.get().jobs()) {
            if (trade.equals(job.requiredBuilding())) buildings.add(job.requiredBuilding());
        }
        if (buildings.isEmpty()) buildings.add(trade);

        Set<Item> selling = new LinkedHashSet<>();
        Set<Item> buying = new LinkedHashSet<>();

        for (String defId : buildings) {
            BuildingDef def = BuildingDataHandler.get(defId).orElse(null);
            if (def == null) continue;
            for (ProductionEntry e : def.production) selling.add(e.item());
            for (TransformationRecipe r : def.transformations) {
                selling.add(r.outputItem());
                // Fully qualified: our ItemCost and Minecraft's trading ItemCost share a name, and
                // importing both would be a coin toss over which one a reader assumes.
                for (org.dawnoftime.onceuponatown.town.ItemCost in : r.inputs()) {
                    buying.add(in.item());
                }
            }
        }
        // Somebody does not buy back what they make; that is a loop the player could farm.
        buying.removeAll(selling);

        for (Item item : selling) {
            int price = TradePriceDataHandler.getBuyPrice(item);
            int lot = Math.max(1, TradePriceDataHandler.getQuantity(item));
            if (!Currency.quotable(price)) continue;
            ItemStack cost = Currency.price(price);
            offers.add(new MerchantOffer(
                new ItemCost(cost.getItem(), cost.getCount()),
                new ItemStack(item, Math.min(64, lot)),
                uses, 0, 0.0f));
        }

        for (Item item : buying) {
            int price = TradePriceDataHandler.getSellPrice(item);
            int lot = Math.max(1, TradePriceDataHandler.getQuantity(item));
            if (!Currency.quotable(price)) continue;
            offers.add(new MerchantOffer(
                new ItemCost(item, Math.min(64, lot)),
                Currency.price(price),
                uses, 0, 0.0f));
        }
        return offers;
    }

    /** Whether this person has anything to trade at all. Cheap enough to ask before opening. */
    public static boolean deals(String trade, int skill) {
        return !offersFor(trade, skill).isEmpty();
    }
}
