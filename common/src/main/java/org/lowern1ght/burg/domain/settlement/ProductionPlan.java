package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A pure-arithmetic view of one Settlement building's production line:
 * a list of {@link ProductionRule}s plus the village-wide bonus
 * multiplier that scales every rule's per-tick amount.
 *
 * <p>This is the domain helper {@code ProductionManager} calls into
 * when it needs to know "what did this building produce this tick".
 * The plan itself does not perform the production — it computes the
 * <em>due</em> outputs and their scaled amounts, and returns them as
 * an immutable map keyed by {@link ItemId}. The Minecraft-flavoured
 * write-back into {@code PlacedBuilding.stock} is the adapter's job,
 * not the domain's.
 *
 * <p>{@link #computeDueOutputs(long, long)} is a pure function: same
 * {@code (gameTime, lastTick)} → same {@code Map<ItemId, Integer>}.
 * No I/O, no {@code net.minecraft} import, no current-time read. This
 * is the carve the
 * {@code settlement-production-domain} change exists to deliver
 * (ADR-0015) — the production tick's arithmetic is now exercisable on
 * a bare JVM through {@code ProductionPlanTest}.
 *
 * <p>Multipliers are passed in as plain doubles so the caller (the
 * tick adapter) can fold the village-wide bonus, the per-instance
 * bonus, and the worker / skill bonus into one product before
 * consulting the plan. The plan does not look up bonuses on its own;
 * the domain is data in, data out.
 */
public final class ProductionPlan {

    /**
     * Empty plan — the additive default for a building with no
     * production rules (a garden, a street, a well). A plan loaded
     * from a building with no rules produces no outputs.
     */
    public static final ProductionPlan EMPTY =
        new ProductionPlan(List.of(), 1.0);

    private final List<ProductionRule> rules;
    private final double bonusMultiplier;

    public ProductionPlan(List<ProductionRule> rules, double bonusMultiplier) {
        Objects.requireNonNull(rules, "rules");
        if (bonusMultiplier < 0.0) {
            throw new IllegalArgumentException(
                "ProductionPlan.bonusMultiplier must be non-negative; got "
                    + bonusMultiplier);
        }
        this.rules = List.copyOf(rules);
        this.bonusMultiplier = bonusMultiplier;
    }

    /** Read-only view of the rules. Iteration order is insertion order. */
    public List<ProductionRule> rules() {
        return Collections.unmodifiableList(rules);
    }

    /** The village-wide bonus multiplier (1.0 = no bonus). */
    public double bonusMultiplier() {
        return bonusMultiplier;
    }

    /**
     * True iff this plan has no rules and would produce nothing. Mirrors
     * the {@code rules.isEmpty()} short-circuit the legacy tick loop
     * already performed.
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    /**
     * Compute the due outputs for a single tick at {@code gameTime},
     * given the previous tick the plan was evaluated on. Returns an
     * immutable map from {@link ItemId} to the integer amount to
     * add to that building's stock.
     *
     * <p>A rule is <em>due</em> when {@code gameTime} is a multiple of
     * its {@link ProductionRule#everyTicks()}. The plan does not
     * reason about elapsed ticks between calls — every rule evaluates
     * independently on the current {@code gameTime}, the same way
     * {@code ProductionManager.tick} has always done. (The lastTick
     * parameter is kept on the signature for a future carve that may
     * move to an interval-based accumulator; today it is unused
     * beyond a forward-compatibility read by the caller.)
     *
     * <p>The bonus multiplier scales every due output's
     * {@link ProductionRule#amount()}, then rounds to the nearest
     * integer with {@link Math#round(double)}. A non-positive
     * {@code bonusMultiplier} collapses to zero output — the
     * arithmetic is the same as the legacy tick loop's
     * "effectiveTicks > 0" gate for cadence, applied here to the
     * scaled amount.
     *
     * <p>Pure: no {@code PlacedBuilding}, no {@code Item}, no
     * registry. The caller resolves {@code Item} via
     * {@code BuiltInRegistries.ITEM.getKey(item)} at the
     * {@code Town} facade edge.
     */
    public Map<ItemId, Integer> computeDueOutputs(long gameTime, long lastTick) {
        if (rules.isEmpty() || bonusMultiplier <= 0.0) {
            return Map.of();
        }
        Map<ItemId, Integer> outputs = new LinkedHashMap<>();
        for (ProductionRule rule : rules) {
            if (!rule.isDue(gameTime)) continue;
            int scaled = (int) Math.round(rule.amount() * bonusMultiplier);
            if (scaled <= 0) continue;
            outputs.merge(rule.output(), scaled, Integer::sum);
        }
        return outputs.isEmpty() ? Map.of() : Collections.unmodifiableMap(outputs);
    }
}
