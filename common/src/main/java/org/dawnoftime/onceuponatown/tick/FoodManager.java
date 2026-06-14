package org.dawnoftime.onceuponatown.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.datapack.BuildingDataHandler;
import org.dawnoftime.onceuponatown.network.NetworkHelper;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.FoodRegistry;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.LevelTowns;
import org.dawnoftime.onceuponatown.town.PlacedBuilding;
import org.dawnoftime.onceuponatown.town.Town;
import org.dawnoftime.onceuponatown.town.TownInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FoodManager {

    // Fires once per in-game day at sunrise (tick 6000 within each 24000-tick day).
    public static void tick(Town town, ServerLevel level, long gameTime, long anchorKey) {
        if (gameTime % 24000 != 6000) return;

        TownInventory inv = town.getTownInventory();

        float totalFoodDemandFloat = 0f;
        int totalResidentsCounted = 0;
        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
            int res = stats.resolvedResidents();
            if (res > 0) {
                totalResidentsCounted += res;
                totalFoodDemandFloat += res * stats.resolvedConsumptionPerResident();
            }
        }
        int foodUnitsToDrain = (int) Math.ceil(totalFoodDemandFloat);

        int availableFoodUnits = 0;
        for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
            availableFoodUnits += inv.getStock(fEntry.getKey()) * fEntry.getValue();
        }

        int newActiveResidents;
        if (foodUnitsToDrain == 0) {
            newActiveResidents = totalResidentsCounted;
        } else if (availableFoodUnits >= foodUnitsToDrain) {
            newActiveResidents = totalResidentsCounted;
        } else {
            newActiveResidents = (int) Math.floor((float) availableFoodUnits * totalResidentsCounted / totalFoodDemandFloat);
        }
        newActiveResidents = Math.max(0, Math.min(totalResidentsCounted, newActiveResidents));

        int toDrainUnits = Math.min(foodUnitsToDrain, availableFoodUnits);
        if (toDrainUnits > 0) {
            List<ItemCost> drainCosts = new ArrayList<>();
            int remaining = toDrainUnits;
            for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
                if (remaining <= 0) break;
                net.minecraft.world.item.Item foodItem = fEntry.getKey();
                int fuv = fEntry.getValue();
                int stock = inv.getStock(foodItem);
                if (stock == 0) continue;
                int unitsFromThis = stock * fuv;
                if (unitsFromThis >= remaining) {
                    drainCosts.add(new ItemCost(foodItem, (int) Math.ceil((double) remaining / fuv)));
                    remaining = 0;
                } else {
                    drainCosts.add(new ItemCost(foodItem, stock));
                    remaining -= unitsFromThis;
                }
            }
            if (!drainCosts.isEmpty()) inv.removeStock(drainCosts);
        }

        int prevActive = town.getActiveResidents();
        town.setActiveResidents(newActiveResidents);
        LevelTowns.get(level).markDirty();

        // Only push citizen update when active count actually changed.
        if (newActiveResidents != prevActive) {
            BlockPos anchorPos = BlockPos.of(anchorKey);
            NetworkHelper.pushCitizenUpdateToWatchers(level, town, anchorPos);
        }
    }
}
