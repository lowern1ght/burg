package org.lowern1ght.burg.town;

import net.minecraft.world.item.Item;
import org.lowern1ght.burg.datapack.BuildingDataHandler;

import java.util.List;
import java.util.Map;

public class TownInventory {
    private final List<PlacedBuilding> buildings;
    private final Map<Item, Integer> reserve;
    // ADR-0013 — fired after any mutation of {@code reserve} so the Town's
    // StockLedger cache stays in sync. Null is treated as a no-op so the
    // class remains usable in tests / one-off constructions without the
    // domain view; production callers (Town.getTownInventory) pass the
    // sync hook.
    private final Runnable afterReserveMutate;

    public TownInventory(List<PlacedBuilding> buildings, Map<Item, Integer> reserve, Runnable afterReserveMutate) {
        this.buildings = buildings;
        this.reserve = reserve;
        this.afterReserveMutate = afterReserveMutate == null ? () -> {} : afterReserveMutate;
    }

    public int getStock(Item item) {
        return buildings.stream().mapToInt(b -> b.getStock(item)).sum()
            + reserve.getOrDefault(item, 0);
    }

    // Sum of capacity_stacks * 64 for this item across all placed buildings with that production entry.
    // Each producing slot also receives the town-wide stock bonus (extra stacks from granaries etc.).
    public int getMaxStock(Item item) {
        int townStockBonus = buildings.stream()
            .mapToInt(b -> {
                BuildingDef def = BuildingDataHandler.get(b.getDefId()).orElse(null);
                return def == null ? 0 : b.resolvedStockBonus(def);
            })
            .sum();
        return buildings.stream()
            .mapToInt(b -> {
                BuildingDef def = BuildingDataHandler.get(b.getDefId()).orElse(null);
                if (def == null) return 0;
                return def.production.stream()
                    .filter(p -> p.item() == item)
                    .mapToInt(p -> (p.capacityStacks() + townStockBonus) * 64)
                    .sum();
            })
            .sum();
    }

    public void addStock(List<ItemCost> costs) {
        for (ItemCost cost : costs) {
            reserve.merge(cost.item(), cost.amount(), Integer::sum);
        }
        afterReserveMutate.run();
    }

    public boolean hasStock(List<ItemCost> costs) {
        return costs.stream().allMatch(cost -> getStock(cost.item()) >= cost.amount());
    }

    // Drains from buildings first (fullest first), then from reserve
    public void removeStock(List<ItemCost> costs) {
        for (ItemCost cost : costs) {
            int remaining = cost.amount();
            List<PlacedBuilding> sorted = buildings.stream()
                .sorted((a, b) -> b.getStock(cost.item()) - a.getStock(cost.item()))
                .toList();
            for (PlacedBuilding building : sorted) {
                if (remaining <= 0) break;
                remaining -= building.drain(cost.item(), remaining);
            }
            if (remaining > 0 && reserve.containsKey(cost.item())) {
                int inReserve = reserve.getOrDefault(cost.item(), 0);
                int taken = Math.min(inReserve, remaining);
                reserve.put(cost.item(), inReserve - taken);
            }
        }
        afterReserveMutate.run();
    }
}
