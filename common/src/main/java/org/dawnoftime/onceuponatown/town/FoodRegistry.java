package org.dawnoftime.onceuponatown.town;

import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.datapack.FoodListDataHandler;

import java.util.Map;
import java.util.Set;

// Thin facade over FoodListDataHandler. Data is now fully driven by food_list.json.
public class FoodRegistry {

    public static int getFuv(Item item) {
        return FoodListDataHandler.getFuv(item);
    }

    public static Set<Map.Entry<Item, Integer>> entriesInOrder() {
        return FoodListDataHandler.entriesInOrder();
    }
}
