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
import org.dawnoftime.onceuponatown.town.TownLogEntry;
import org.dawnoftime.onceuponatown.town.TownLogEntry.TownLogType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FoodManager {

    /**
     * A day's food demand, in food units, summed over the buildings.
     *
     * <p>Extracted verbatim from the feeding pass so that {@link Settlers} can ask "could this
     * town feed one more mouth" without keeping a second idea of what food costs. Two copies of
     * this sum would drift, and the one that drifted would be the one nobody was watching.
     *
     * <p>Note that it bills the town for its <b>beds</b>, not for the people on its roll: the
     * figure comes from {@code resolvedResidents()} per building. That was the only meaning
     * available while population was a number, and it is worth revisiting now that it is a list
     * of real people — but changing it moves the balance, so it is left alone here.
     */
    public static float residentFoodDemand(Town town) {
        float demand = 0f;
        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
            int res = stats.resolvedResidents();
            if (res > 0) demand += res * stats.resolvedConsumptionPerResident();
        }
        return demand;
    }

    /** Beds, summed the same way the demand is, so the two always agree about who is counted. */
    public static int residentCapacity(Town town) {
        int total = 0;
        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null) continue;
            total += def.resolveAtLevel(building.getUpgradeLevel()).resolvedResidents();
        }
        return total;
    }

    /** Everything edible in the town store, converted to food units by its FUV. */
    public static int availableResidentFoodUnits(Town town) {
        int units = 0;
        TownInventory inv = town.getTownInventory();
        for (Map.Entry<net.minecraft.world.item.Item, Integer> e : FoodRegistry.residentEntriesInOrder()) {
            units += inv.getStock(e.getKey()) * e.getValue();
        }
        return units;
    }

    // Fires at each tick listed in feeding_schedule (ticks within a 24000-tick day).
    public static void tick(Town town, ServerLevel level, long gameTime, long anchorKey) {
        if (!FoodRegistry.getFeedingSchedule().contains(gameTime % 24000)) return;

        TownInventory inv = town.getTownInventory();

        // Resident section: compute demand and drain from resident food pool (strongest FUV first).
        float totalFoodDemandFloat = residentFoodDemand(town);
        int totalResidentsCounted = residentCapacity(town);
        int foodUnitsToDrain = (int) Math.ceil(totalFoodDemandFloat);

        int availableFoodUnits = availableResidentFoodUnits(town);

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
            for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.residentEntriesInOrder()) {
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

        // Herd section: independent pool from herd food list (strongest FUV first).
        // availableHerdFoodUnits is computed before any herd drains; buildings compete sequentially.
        int availableHerdFoodUnits = 0;
        for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.herdEntriesInOrder()) {
            availableHerdFoodUnits += inv.getStock(fEntry.getKey()) * fEntry.getValue();
        }
        int totalHerdDrained = 0;
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
            } else if (availableHerdFoodUnits >= demand) {
                List<ItemCost> herdDrain = new ArrayList<>();
                int herdRemaining = demand;
                for (Map.Entry<net.minecraft.world.item.Item, Integer> fEntry : FoodRegistry.herdEntriesInOrder()) {
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
                availableHerdFoodUnits -= demand;
                totalHerdDrained += demand;
                building.setHerdFed(true);
            } else {
                building.setHerdFed(false);
            }
            if (building.isHerdFed() != wasFed) herdChanged = true;
        }

        int totalConsumed = toDrainUnits + totalHerdDrained;
        if (totalConsumed > 0) {
            BlockPos anchorPos = BlockPos.of(anchorKey);
            TownLogEntry foodLog = new TownLogEntry(TownLogType.FOOD_CONSUMED, String.valueOf(totalConsumed), level.getGameTime());
            town.addLogEntry(foodLog);
            NetworkHelper.pushLogEntryToWatchers(level, town, anchorPos, foodLog);
        }

        LevelTowns.get(level).markDirty();

        // Push citizen update when active residents or any herd fed status changed.
        if (newActiveResidents != prevActive || herdChanged) {
            BlockPos anchorPos = BlockPos.of(anchorKey);
            NetworkHelper.pushCitizenUpdateToWatchers(level, town, anchorPos);
        }
    }
}
