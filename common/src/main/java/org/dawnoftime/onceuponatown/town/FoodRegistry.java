package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

// Hardcoded food unit value (FUV) registry. Iteration order = drain order (lowest FUV first).
public class FoodRegistry {

    private static final Map<Item, Integer> FUV = new LinkedHashMap<>();

    static {
        FUV.put(Items.APPLE, 1);
        FUV.put(Items.CARROT, 1);
        FUV.put(Items.POTATO, 1);
        FUV.put(Items.COOKED_BEEF, 2);
        FUV.put(Items.COOKED_MUTTON, 2);
        FUV.put(Items.COOKED_PORKCHOP, 2);
        FUV.put(Items.BAKED_POTATO, 2);
    }

    public static int getFuv(Item item) {
        return FUV.getOrDefault(item, 0);
    }

    // Returns entries in insertion order (FUV 1 items before FUV 2 items).
    public static Set<Map.Entry<Item, Integer>> entriesInOrder() {
        return FUV.entrySet();
    }
}
