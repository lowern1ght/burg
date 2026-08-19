package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.datapack.SettlerJobsDataHandler;
import org.lowern1ght.burg.domain.settlement.ProductionPlan;
import org.lowern1ght.burg.domain.settlement.ProductionRule;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.network.NetworkHelper;
import org.lowern1ght.burg.town.BuildingDef;
import org.lowern1ght.burg.town.ItemCost;
import org.lowern1ght.burg.town.LevelTowns;
import org.lowern1ght.burg.town.PlacedBuilding;
import org.lowern1ght.burg.town.ProductionEntry;
import org.lowern1ght.burg.town.Town;
import org.lowern1ght.burg.town.TownInventory;
import org.lowern1ght.burg.town.TransformationRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductionManager {

    // Minimum ticks between stock UI pushes per town to avoid flooding during production bursts.
    private static final int UI_PUSH_COOLDOWN = 60;
    private static final Map<Long, Long> lastUiPushTick = new HashMap<>();


    /**
     * What the worker at this building is worth to its output.
     *
     * <p>This is the line that stops the axe swing being decoration. Before it, production ran on
     * a timer and looked only at a resident HEADCOUNT, so it made no difference whether anybody
     * ever stood at the bench.
     *
     * <p>Read off the building's own stamp rather than by finding its worker, because this runs
     * over every building on a timer and must not depend on an entity being loaded — a workshop
     * does not stop having been worked because the person who worked it walked into an unloaded
     * chunk.
     *
     * <p><b>Only buildings that HAVE a job defined are judged on being manned.</b> A well, a
     * street, a garden or anything else no settler can work at produces at its ordinary rate; the
     * alternative would silently halve output for buildings nobody could ever staff.
     */
    private static double workerMultiplier(PlacedBuilding building, long gameTime) {
        SettlerJobsDataHandler.Config cfg = SettlerJobsDataHandler.get();
        if (!SettlerJobsDataHandler.hasJob(building.getDefId())) return 1.0;
        if (!building.isManned(gameTime, cfg.mannedWindowTicks())) return cfg.unmannedOutput();
        return 1.0 + building.getLastWorkerSkill() * cfg.skillBonusPerLevel();
    }

    /**
     * Builds the {@link ProductionPlan} for one production entry. The
     * legacy entry shape is {@code Item}-keyed; the domain plan is
     * {@link ItemId}-keyed. This is the seam: the {@code Town} facade
     * resolves the registry key once and the domain helper does the
     * arithmetic. A future carve may fold the whole {@code for(...)
     * stats.production()} loop into a single plan.buildAndApply call;
     * today we extract one rule at a time to keep the touch minimal
     * (ADR-0015 non-goal: no big-bang tick rewrite).
     */
    static ProductionPlan buildProductionPlan(ProductionEntry entry, double totalMultiplier) {
        ItemId outputId = ItemId.of(BuiltInRegistries.ITEM.getKey(entry.item()).toString());
        ProductionRule rule = new ProductionRule(
            outputId, entry.amount(), entry.everyTicks(), entry.capacityItems());
        return new ProductionPlan(List.of(rule), totalMultiplier);
    }

    /**
     * Convenience bridge: run the entry through
     * {@link #buildProductionPlan(ProductionEntry, double)} and return
     * the amount the tick should add to {@code building}'s stock for
     * {@code entry} on {@code gameTime}. Returns 0 when the rule is
     * not due this tick (i.e. the plan emits nothing). The capacity
     * cap is still the adapter's job — the helper returns the raw
     * scaled amount.
     */
    static int computeBoostedAmount(ProductionEntry entry, double totalMultiplier,
                                     long gameTime, long lastTick) {
        ProductionPlan plan = buildProductionPlan(entry, totalMultiplier);
        ItemId outputId = ItemId.of(BuiltInRegistries.ITEM.getKey(entry.item()).toString());
        Integer due = plan.computeDueOutputs(gameTime, lastTick).get(outputId);
        return due != null ? due : 0;
    }

    public static boolean tick(Town town, ServerLevel level, long gameTime, long anchorKey) {
        TownInventory inv = town.getTownInventory();
        boolean changed = false;

        int currentActiveResidents = town.getActiveResidents();
        double bonusMultiplier = 1.0 + town.getBuildings().stream()
            .mapToDouble(b -> {
                BuildingDef bDef = BuildingDataHandler.get(b.getDefId()).orElse(null);
                if (bDef == null) return 0.0;
                double resolved = b.resolvedProductionBonus(bDef);
                if (resolved == 0.0) return 0.0;
                if (bDef.requiredResidents > 0 && currentActiveResidents < bDef.requiredResidents) return 0.0;
                return resolved;
            })
            .sum();

        for (PlacedBuilding building : town.getBuildings()) {
            BuildingDef def = BuildingDataHandler.get(building.getDefId()).orElse(null);
            if (def == null) continue;
            BuildingDef.ResolvedBuildingStats stats = def.resolveAtLevel(building.getUpgradeLevel());
            // Herd buildings that were not fed produce nothing; transformations are never blocked.
            if (stats.resolvedHerd() > 0 && !building.isHerdFed()) {
                if (def.isTransformer() && def.transformEveryTicks > 0 && gameTime % def.transformEveryTicks == 0) {
                    changed |= tickTransformer(building, def, inv);
                }
                continue;
            }
            for (ProductionEntry entry : stats.production()) {
                if (entry.everyTicks() <= 0) continue;
                int effectiveTicks = stats.totalCadenceMultiplier() > 0
                    ? (int) Math.max(1, Math.round(entry.everyTicks() / (1.0 + stats.totalCadenceMultiplier())))
                    : entry.everyTicks();
                if (gameTime % effectiveTicks != 0) continue;
                int current = inv.getStock(entry.item());
                int max = inv.getMaxStock(entry.item());
                if (current < max) {
                    double totalMultiplier = bonusMultiplier
                        * building.getInstanceProductionMultiplier()
                        * workerMultiplier(building, gameTime);
                    int boostedAmount = computeBoostedAmount(
                        entry, totalMultiplier, gameTime, gameTime - effectiveTicks);
                    if (boostedAmount <= 0) continue;
                    building.forceAdd(entry.item(), Math.min(boostedAmount, max - current));
                    changed = true;
                }
            }

            if (def.isTransformer() && def.transformEveryTicks > 0
                    && gameTime % def.transformEveryTicks == 0) {
                changed |= tickTransformer(building, def, inv);
            }
        }

        if (changed) {
            LevelTowns.get(level).markDirty();
            BlockPos anchorPos = BlockPos.of(anchorKey);
            long lastPush = lastUiPushTick.getOrDefault(anchorKey, 0L);
            if (gameTime - lastPush >= UI_PUSH_COOLDOWN) {
                NetworkHelper.pushStockToWatchers(level, town, anchorPos);
                lastUiPushTick.put(anchorKey, gameTime);
            }
        }

        return changed;
    }

    private static boolean tickTransformer(PlacedBuilding building, BuildingDef def, TownInventory inv) {
        int buildingLevel = building.getUpgradeLevel();
        Map<net.minecraft.world.item.Item, Integer> budget = new HashMap<>();
        for (TransformationRecipe recipe : def.transformations) {
            if (!recipe.isActive(buildingLevel)) continue;
            for (ItemCost input : recipe.inputs()) {
                budget.computeIfAbsent(input.item(), item -> (int)(inv.getStock(item) * def.transformInputRatio));
            }
        }

        Map<net.minecraft.world.item.Item, Integer> consumed = new HashMap<>();
        boolean anyProduced = false;
        boolean passProduced;
        do {
            passProduced = false;
            for (TransformationRecipe recipe : def.transformations) {
                if (!recipe.isActive(buildingLevel)) continue;
                boolean canAfford = recipe.inputs().stream()
                    .allMatch(input -> budget.getOrDefault(input.item(), 0) >= input.amount());
                int currentOutput = building.getStock(recipe.outputItem());
                if (!canAfford || currentOutput + recipe.outputAmount() > recipe.outputCapacityItems()) continue;
                for (ItemCost input : recipe.inputs()) {
                    budget.merge(input.item(), -input.amount(), Integer::sum);
                    consumed.merge(input.item(), input.amount(), Integer::sum);
                }
                building.forceAdd(recipe.outputItem(), recipe.outputAmount());
                passProduced = true;
                anyProduced = true;
            }
        } while (passProduced);

        if (!consumed.isEmpty()) {
            List<ItemCost> costs = new ArrayList<>();
            consumed.forEach((item, amount) -> costs.add(new ItemCost(item, amount)));
            inv.removeStock(costs);
        }

        return anyProduced;
    }

    // TODO(act4-followup-1): route the transformer loop through the domain
    // TransformationRule (domain/settlement/TransformationRule.java) once the
    // budget and reserve-stock write paths are themselves domain-typed.
    //
    // The clean shape would be: pre-build one TransformationRule per active
    // TransformationRecipe (Item → ItemId via BuiltInRegistries), snapshot the
    // MC budget as a StockLedger (Item → ItemId translation), call
    // rule.canApply(snapshot) for the affordance pre-check, and use
    // rule.inputTotals() for the multi-pass budget drain. The output side
    // stays a `building.forceAdd` call — rule.apply(stock) puts the output
    // into StockLedger (reserve), but the legacy behaviour adds the output
    // to PlacedBuilding.stock (a per-instance cap, not a reserve value), so
    // the rewrite would have to also redirect the output. That's a real
    // behavioural change, not a refactor.
    //
    // Today the MC-typed path here works correctly: the multi-pass loop,
    // the per-input-ratio budget, the per-building output capacity, and the
    // final `inv.removeStock` are all wired and tested through the existing
    // transformer datapacks. Leaving this carve for the future follow-up so
    // the wiring lands in one focused PR with the behavioural change
    // spelled out, not bolted onto a no-op refactor.
}
