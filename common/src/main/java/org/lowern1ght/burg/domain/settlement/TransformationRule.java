package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A Minecraft-free description of one Settlement building's
 * transformation pass: a list of input item costs, the output
 * (item + amount), and a per-output capacity.
 *
 * <p>Mirrors the shape of {@code town.TransformationRecipe} but with
 * {@link ItemId} in place of {@code net.minecraft.world.item.Item}.
 * Like {@link ProductionRule} and {@code StockLedger}, this is a
 * read-side projection the domain layer can reason about without a
 * Minecraft classpath. The {@code Town} facade owns the
 * {@code Item}-keyed recipe for NBT round-tripping.
 *
 * <p>The production tick's transformer loop — the
 * "consume inputs / produce output / cap on per-building capacity /
 * fail on insufficient input" path — is a complex iterator with
 * multi-pass and budget semantics. This record is the
 * <em>shape</em> of one recipe; the full iterative application lives
 * in the tick adapter (still {@code ProductionManager.tickTransformer}
 * for this carve). The pure helper {@link #apply(StockLedger)} below
 * is the minimum the domain needs to test the failure case
 * "transformer fired but inputs were not available" on a bare JVM.
 *
 * <p>No Minecraft imports.
 */
public record TransformationRule(
    List<StockCost> inputs,
    ItemId output,
    int outputAmount,
    int outputCapacityItems
) {

    /**
     * One input line of a {@link TransformationRule}: a required
     * {@link ItemId} and a positive amount. {@code amount} is
     * checked at construction time so the apply path can rely on
     * every input being drainable.
     */
    public record StockCost(ItemId item, int amount) {
        public StockCost {
            Objects.requireNonNull(item, "item");
            if (amount <= 0) {
                throw new IllegalArgumentException(
                    "StockCost.amount must be positive; got " + amount);
            }
        }
    }

    public TransformationRule {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(output, "output");
        if (outputAmount <= 0) {
            throw new IllegalArgumentException(
                "TransformationRule.outputAmount must be positive; got "
                    + outputAmount);
        }
        if (outputCapacityItems < 0) {
            throw new IllegalArgumentException(
                "TransformationRule.outputCapacityItems must be non-negative; got "
                    + outputCapacityItems);
        }
        // Defensive copy so a caller that mutates the source list after
        // construction does not change the rule's behaviour.
        inputs = List.copyOf(inputs);
    }

    /**
     * Pure single-shot apply: consumes the inputs from {@code stock}
     * and returns a new ledger with the output added. Throws
     * {@link IllegalStateException} if the ledger has less of any
     * input than required — the same deterministic failure
     * {@link StockLedger#take} raises. Outputs are added without
     * checking the per-building capacity; the adapter is responsible
     * for the capacity cap because capacity is a per-instance value
     * (the {@code PlacedBuilding.stock} cap), not a property of the
     * rule.
     *
     * <p>This helper is intentionally a single shot, not a multi-pass
     * loop. The full transformer logic — budget-then-drain across
     * every active recipe on the building — is the tick adapter's
     * job and will be carved into a separate helper in a future
     * change. Today the helper pins the failure case ("insufficient
     * input ⇒ deterministic failure") and the success case ("all
     * inputs drained, output added") so the rule's contract is
     * testable on a bare JVM.
     */
    public StockLedger apply(StockLedger stock) {
        Objects.requireNonNull(stock, "stock");
        // Take every input. The first one that fails rolls back nothing
        // (StockLedger is immutable; the input ledger is unchanged on
        // the failure path) — the caller sees the original ledger
        // and the throw.
        StockLedger drained = stock;
        for (StockCost cost : inputs) {
            drained = drained.take(cost.item(), cost.amount());
        }
        return drained.add(output, outputAmount);
    }

    /**
     * True iff {@code stock} has enough of every input to fire this
     * rule. A non-mutating pre-check the adapter can use to skip
     * recipes that would obviously fail before the full
     * {@link #apply(StockLedger)} runs. Returns the same result as
     * the {@code IllegalStateException} path in {@code apply}, with
     * no side effects.
     */
    public boolean canApply(StockLedger stock) {
        Objects.requireNonNull(stock, "stock");
        for (StockCost cost : inputs) {
            if (stock.get(cost.item()) < cost.amount()) {
                return false;
            }
        }
        return true;
    }

    /** Read-only view of the inputs in declaration order. */
    public List<StockCost> inputs() {
        return Collections.unmodifiableList(inputs);
    }

    /**
     * Read-only view of the input totals, summed by item. Useful for
     * the multi-pass budget loop in the tick adapter; the
     * transformation pass needs the per-input total to know how
     * much of {@code stock} is fair game across the loop.
     */
    public Map<ItemId, Integer> inputTotals() {
        if (inputs.isEmpty()) return Map.of();
        Map<ItemId, Integer> totals = new LinkedHashMap<>();
        for (StockCost cost : inputs) {
            totals.merge(cost.item(), cost.amount(), Integer::sum);
        }
        return Collections.unmodifiableMap(totals);
    }
}
