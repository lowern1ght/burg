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

        // Compute remaining food after resident drain, then feed herd buildings in order.
        int remainingFoodUnits = 0;
        for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
            remainingFoodUnits += inv.getStock(fEntry.getKey()) * fEntry.getValue();
        }
        boolean herdChanged = false;
        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
            if (stats.resolvedHerd() <= 0) continue;
            boolean wasFed = building.isHerdFed();
            int demand = (int) Math.ceil(stats.resolvedHerd() * stats.resolvedConsumptionPerHerd());
            if (demand == 0) {
                building.setHerdFed(true);
            } else if (remainingFoodUnits >= demand) {
                List<ItemCost> herdDrain = new ArrayList<>();
                int herdRemaining = demand;
                for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.entriesInOrder()) {
                    if (herdRemaining <= 0) break;
                    net.minecraft.world.item.Item foodItem = fEntry.getKey();
                    int fuv = fEntry.getValue();
                    int stock = inv.getStock(foodItem);
                    if (stock == 0) continue;
                    int unitsFromThis = stock * fuv;
                    if (unitsFromThis >= herdRemaining) {
                        herdDrain.add(new ItemCost(foodItem, (int) Math.ceil((double) herdRemaining / fuv)));
                        herdRemaining = 0;
                    } else {
                        herdDrain.add(new ItemCost(foodItem, stock));
                        herdRemaining -= unitsFromThis;
                    }
                }
                if (!herdDrain.isEmpty()) inv.removeStock(herdDrain);
                remainingFoodUnits -= demand;
                building.setHerdFed(true);
            } else {
                building.setHerdFed(false);
            }
            if (building.isHerdFed() != wasFed) herdChanged = true;
        }

        LevelTowns.get(level).markDirty();

        // Push citizen update when active residents or any herd fed status changed.
        if (newActiveResidents != prevActive || herdChanged) {
            BlockPos anchorPos = BlockPos.of(anchorKey);
            NetworkHelper.pushCitizenUpdateToWatchers(level, town, anchorPos);
        }
    }
}
