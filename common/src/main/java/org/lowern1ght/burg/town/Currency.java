package org.lowern1ght.burg.town;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Inventory;

/**
 * Money, in one place.
 *
 * <p><b>Gold, not emeralds</b>, by the owner's ruling, and the reasoning holds up: an emerald as
 * peasant currency is Minecraft's own oddity, and gold is the closest thing in the game that
 * reads as coin. It is also <em>mineable</em>, which emeralds effectively are not — the player
 * can dig money rather than only ever receive it from a villager. That is a real shift in the
 * economy and a deliberate one.
 *
 * <p><b>Two denominations, because one makes everything cheap cost the same.</b> With a single
 * unit and integer prices, a loaf and a plank both round to 1 and the whole bottom of the scale
 * collapses. A nugget is the penny and an ingot the shilling, at vanilla's own nine-to-one — so a
 * loaf can cost two pennies and a bed thirty, and a large purchase does not turn into sixty items
 * in the hand.
 *
 * <p>Everything that touches money goes through here. The emerald it replaces was written into
 * three packet handlers and six places in the hub screen, which is exactly how a currency becomes
 * impossible to change; this class exists so that never happens again.
 */
public final class Currency {

    /** The penny. Prices are quoted in these. */
    public static final Item COIN = Items.GOLD_NUGGET;

    /** The shilling. Vanilla's own crafting ratio, so the player already knows it. */
    public static final Item PURSE_COIN = Items.GOLD_INGOT;

    public static final int COINS_PER_PURSE = 9;

    private Currency() {
    }

    /** How much money a player is carrying, counted in pennies. */
    public static int inHand(Inventory inventory) {
        int coins = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(COIN)) coins += s.getCount();
            else if (s.is(PURSE_COIN)) coins += s.getCount() * COINS_PER_PURSE;
        }
        return coins;
    }

    /**
     * The stack a price of this many pennies should be ASKED for.
     *
     * <p>Quoted in the larger coin once it divides evenly, because a trade window asking for
     * eighteen nuggets when it could ask for two ingots reads as clutter — and a
     * {@code MerchantOffer} takes one cost stack, so the choice has to be made here.
     */
    public static ItemStack price(int pennies) {
        if (pennies >= COINS_PER_PURSE && pennies % COINS_PER_PURSE == 0) {
            return new ItemStack(PURSE_COIN, Math.min(64, pennies / COINS_PER_PURSE));
        }
        return new ItemStack(COIN, Math.min(64, Math.max(1, pennies)));
    }

    /**
     * Whether a price can be expressed at all in one stack.
     *
     * <p>A merchant offer is a single stack, so anything over 64 ingots — 576 pennies — cannot be
     * asked for. Rather than silently quote the wrong number, callers skip the offer and say so.
     */
    public static boolean quotable(int pennies) {
        return pennies > 0 && pennies <= 64 * COINS_PER_PURSE;
    }

    /** Human-readable, for a tooltip or a log line. */
    public static String describe(int pennies) {
        int purses = pennies / COINS_PER_PURSE, coins = pennies % COINS_PER_PURSE;
        if (purses == 0) return coins + "n";
        return coins == 0 ? purses + "g" : purses + "g " + coins + "n";
    }
}
