package org.lowern1ght.burg.tick;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import org.lowern1ght.burg.datapack.BuildingDataHandler;
import org.lowern1ght.burg.datapack.SettlerJobsDataHandler;
import org.lowern1ght.burg.domain.settlement.ProductionPlan;
import org.lowern1ght.burg.domain.settlement.ProductionRule;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.settlement.TransformationRule;
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
import java.util.LinkedHashMap;
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

    /**
     * Result of one transformer pass: the resulting budget ledger (kept pure
     * for transparency / testing), per-rule fire counts, and the per-item
     * total inputs the pass actually consumed (the wire the upstream write
     * back to {@link TownInventory} uses).
     *
     * <p>Package-private, allocation-cheap (an immutable record + two maps
     * only ever filled by the helper that produced this). Used only by
     * {@link #tickTransformer} and its bare-JVM tests today; a future carve
     * may fold this into {@code ProductionManager}'s public shape if
     * {@code Town} starts producing passes for telemetry.
     */
    record TransformerBudgetPass(
        StockLedger nextBudget,
        Map<TransformationRule, Integer> appliedPerRule,
        Map<ItemId, Integer> consumedPerItem
    ) {}

    private static boolean tickTransformer(PlacedBuilding building, BuildingDef def, TownInventory inv) {
        // The transformer pass has TWO halves, and this carve only rewires one:
        //
        //   BUDGET half — pure-domain. The multi-pass loop, the
        //   per-input-ratio budget, the canApply/apply parity, and the
        //   per-item consumed totals all live on a StockLedger now and run
        //   through the domain TransformationRule. `runTransformerBudget`
        //   below is the carved helper; its signature is bare-JVM
        //   (List<TransformationRule>, StockLedger, int[]) so the contract
        //   is testable without Minecraft, which the
        //   `ProductionManagerTransformerTest` covers.
        //
        //   OUTPUT half — MC-typed. Each fired rule increments the
        //   building's per-instance stock (`PlacedBuilding.stock`,
        //   `building.forceAdd`), and the consumed totals drain back to the
        //   town via `TownInventory.removeStock` with MC-typed ItemCost.
        //   The per-instance cap is enforced by the `remaining` array
        //   below; the writing path itself stays MC.
        //
        // The output half is deliberately not migrated here. Migrating it
        // would mean either (a) redirecting the output into the town's
        // reserve StockLedger instead of the per-building stock (a real
        // behavioural change — the cap ceiling disappears), or (b) moving
        // `PlacedBuilding.stock` itself to a domain ledger (out of scope
        // for a budget-only carve). The act-5 follow-up owns the choice;
        // this carve only proves the budget half can be route-tested on a
        // bare JVM.
        int buildingLevel = building.getUpgradeLevel();

        Map<Item, Integer> budgetSourceByItem = new HashMap<>();
        for (TransformationRecipe recipe : def.transformations) {
            if (!recipe.isActive(buildingLevel)) continue;
            for (ItemCost input : recipe.inputs()) {
                budgetSourceByItem.computeIfAbsent(input.item(),
                    item -> (int) (inv.getStock(item) * def.transformInputRatio));
            }
        }
        Map<ItemId, Integer> budgetSource = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : budgetSourceByItem.entrySet()) {
            budgetSource.put(itemIdOf(e.getKey()), e.getValue());
        }
        StockLedger initialBudget = StockLedger.of(budgetSource);

        // Pair each active recipe with its domain rule. The pair order
        // (`activeRules` and `outputItems` share indices) lets `remaining`
        // and the post-pass `forceAdd` loop both refer back to the right
        // MC `Item` without going through `ItemId` again on the way out.
        List<TransformationRule> activeRules = new ArrayList<>();
        List<Item> outputItems = new ArrayList<>();
        for (TransformationRecipe recipe : def.transformations) {
            if (!recipe.isActive(buildingLevel)) continue;
            activeRules.add(toDomainRule(recipe));
            outputItems.add(recipe.outputItem());
        }
        int[] remaining = new int[activeRules.size()];
        for (int i = 0; i < activeRules.size(); i++) {
            TransformationRule r = activeRules.get(i);
            int currentOutput = building.getStock(outputItems.get(i));
            // Floored to >= 0 so a fully-capped rule just sits at zero
            // rather than overflowing on the subtraction.
            remaining[i] = Math.max(0, (r.outputCapacityItems() - currentOutput) / r.outputAmount());
        }

        TransformerBudgetPass pass = runTransformerBudget(activeRules, initialBudget, remaining);

        // Apply outputs (legacy MC-typed sink: per-instance stock).
        for (Map.Entry<TransformationRule, Integer> e : pass.appliedPerRule().entrySet()) {
            int idx = activeRules.indexOf(e.getKey());
            if (idx < 0 || e.getValue() <= 0) continue;
            building.forceAdd(outputItems.get(idx),
                activeRules.get(idx).outputAmount() * e.getValue());
        }

        if (!pass.consumedPerItem().isEmpty()) {
            List<ItemCost> costs = new ArrayList<>();
            for (Map.Entry<ItemId, Integer> e : pass.consumedPerItem().entrySet()) {
                costs.add(new ItemCost(itemOf(e.getKey()), e.getValue()));
            }
            inv.removeStock(costs);
        }

        int totalApplied = pass.appliedPerRule().values().stream().mapToInt(Integer::intValue).sum();
        return totalApplied > 0;
    }

    /**
     * Pure-domain transformer pass: for each rule whose per-tick capacity
     * has not been used up and whose {@link TransformationRule#inputTotals()
     * input totals} the current {@code budget} covers, drain the inputs
     * (via {@link TransformationRule#apply(StockLedger)}) and credit the
     * count. The loop iterates until a full pass yields no applies — the
     * exact multi-pass shape the legacy MC loop had, only on a
     * {@code StockLedger} instead of a {@code Map<Item, Integer>}.
     *
     * <p><b>The pre-check uses {@code inputTotals()}, not
     * {@code canApply()}.</b> {@code canApply} walks the rule's input lines
     * one at a time and returns {@code true} when each line is covered
     * individually, even for a rule with duplicate lines for the same item
     * whose sum the budget can't cover; {@code apply} drains sequentially
     * and would throw {@code IllegalStateException} on the second drain.
     * {@link TransformationRuleMutationTest} pins that known gap. The
     * summed view {@code inputTotals()} restores parity and is what this
     * helper uses as the pre-check, so {@code apply} only runs when it is
     * guaranteed not to throw.
     *
     * <p>Package-private so the bare-JVM test in this package can drive it
     * directly with constructed rules and a {@link StockLedger} budget —
     * the seam between the MC-typed {@code tickTransformer} shell and the
     * pure-domain budget half.
     */
    static TransformerBudgetPass runTransformerBudget(
        List<TransformationRule> rules,
        StockLedger initialBudget,
        int[] remainingFiresPerRule
    ) {
        if (rules.size() != remainingFiresPerRule.length) {
            throw new IllegalArgumentException(
                "rules.size() must equal remainingFiresPerRule.length ("
                    + rules.size() + " vs " + remainingFiresPerRule.length + ")");
        }
        StockLedger budget = initialBudget;
        Map<TransformationRule, Integer> appliedPerRule = new LinkedHashMap<>();
        Map<ItemId, Integer> consumedPerItem = new LinkedHashMap<>();
        boolean passProduced;
        do {
            passProduced = false;
            for (int i = 0; i < rules.size(); i++) {
                TransformationRule rule = rules.get(i);
                int appliedSoFar = appliedPerRule.getOrDefault(rule, 0);
                if (appliedSoFar >= remainingFiresPerRule[i]) continue;

                // Per-item sum pre-check — see the javadoc on the
                // duplicate-line gap above.
                Map<ItemId, Integer> totals = rule.inputTotals();
                boolean budgetOk = true;
                for (Map.Entry<ItemId, Integer> t : totals.entrySet()) {
                    if (budget.get(t.getKey()) < t.getValue()) {
                        budgetOk = false;
                        break;
                    }
                }
                if (!budgetOk) continue;

                budget = rule.apply(budget);
                appliedPerRule.merge(rule, 1, Integer::sum);
                for (Map.Entry<ItemId, Integer> t : totals.entrySet()) {
                    consumedPerItem.merge(t.getKey(), t.getValue(), Integer::sum);
                }
                passProduced = true;
            }
        } while (passProduced);
        return new TransformerBudgetPass(budget, appliedPerRule, consumedPerItem);
    }

    /**
     * Translates a Minecraft {@code Item} into its domain {@link ItemId}
     * using the registry key the {@code Town} facade already uses for
     * {@code reserveStock} NBT round-tripping. Package-private so the
     * direct-from-domain test path can convert rules back if it ever
     * needs to; today only the {@link #tickTransformer} shell reaches
     * into MC.
     */
    static ItemId itemIdOf(Item item) {
        return ItemId.of(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    /**
     * Reverse translation from domain {@link ItemId} back to the MC
     * {@code Item} used by {@link TownInventory#removeStock}. Used only on
     * the way out of the budget pass; bare-JVM tests do not touch this
     * path. {@code BuiltInRegistries.ITEM.get} accepts the canonical
     * {@code "namespace:path"} form {@link ItemId#value} carries.
     */
    private static Item itemOf(ItemId id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.value()));
    }

    /**
     * Builds a domain {@link TransformationRule} from the MC-typed
     * recipe. Same shape — list of {@code StockCost}, output, amount, cap.
     * Package-private so the seam is verifiable from tests if we ever
     * grow one; today the only consumer is {@link #tickTransformer}.
     */
    static TransformationRule toDomainRule(TransformationRecipe recipe) {
        List<TransformationRule.StockCost> inputs = new ArrayList<>(recipe.inputs().size());
        for (ItemCost ic : recipe.inputs()) {
            inputs.add(new TransformationRule.StockCost(itemIdOf(ic.item()), ic.amount()));
        }
        return new TransformationRule(
            inputs,
            itemIdOf(recipe.outputItem()),
            recipe.outputAmount(),
            recipe.outputCapacityItems());
    }
}
