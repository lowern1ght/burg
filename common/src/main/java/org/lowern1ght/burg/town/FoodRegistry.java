package org.lowern1ght.burg.town;

import net.minecraft.world.item.Item;
import org.lowern1ght.burg.datapack.FoodListDataHandler;

import java.util.List;
import java.util.Map;

// Thin facade over FoodListDataHandler. Data is fully driven by food_list.json.
public class FoodRegistry {

    public static List<Map.Entry<Item, Integer>> residentEntriesInOrder() {
        return FoodListDataHandler.residentEntriesInOrder();
    }

    public static List<Map.Entry<Item, Integer>> herdEntriesInOrder() {
        return FoodListDataHandler.herdEntriesInOrder();
    }

    public static List<Long> getFeedingSchedule() {
        return FoodListDataHandler.getFeedingSchedule();
    }
}
