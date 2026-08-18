package org.lowern1ght.burg.domain.settlement;

import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.Objects;

/**
 * One cadence-driven output line of a Settlement building. Mirrors the
 * shape of {@code town.ProductionEntry} but with {@link ItemId} in place
 * of {@code net.minecraft.world.item.Item}, so the domain can reason
 * about what a building produces without a Minecraft classpath.
 *
 * <p>The Minecraft-free shape is the second value object in the
 * Production carve (after {@code StockLedger}). The {@code Town} facade
 * keeps owning the {@code Item}-keyed {@code ProductionEntry} for NBT
 * round-tripping; this record is the read-side projection the
 * production tick will eventually call into, and the substrate for the
 * pure {@code ProductionPlan.computeDueOutputs} function.
 *
 * <p>Capacity is in <em>items</em> (not stacks) inside the domain —
 * the {@code capacityStacks * 64} conversion is the Minecraft-flavoured
 * detail that the {@code Town} facade is responsible for when it
 * constructs a {@code ProductionRule} from a {@code ProductionEntry}.
 * Keeping the rule in items means the tick loop and the
 * {@code ProductionPlan} stay arithmetic on plain integers.
 *
 * <p>No Minecraft imports. Conversion to and from {@code Item} happens
 * at the {@code Town} facade edge via
 * {@code ItemId.of(BuiltInRegistries.ITEM.getKey(item).toString())}.
 */
public record ProductionRule(
    ItemId output,
    int amount,
    long everyTicks,
    int capacityItems
) {

    public ProductionRule {
        Objects.requireNonNull(output, "output");
        if (amount <= 0) {
            throw new IllegalArgumentException(
                "ProductionRule.amount must be positive; got " + amount);
        }
        if (everyTicks <= 0) {
            throw new IllegalArgumentException(
                "ProductionRule.everyTicks must be positive; got " + everyTicks);
        }
        if (capacityItems < 0) {
            throw new IllegalArgumentException(
                "ProductionRule.capacityItems must be non-negative; got " + capacityItems);
        }
    }

    /**
     * True iff {@code gameTime} is a multiple of {@link #everyTicks()}
     * starting from tick 0. Mirrors the legacy check in
     * {@code ProductionManager.tick} so the carve is behaviour-
     * preserving when the helper is wired in.
     */
    public boolean isDue(long gameTime) {
        return gameTime % everyTicks == 0;
    }

    /**
     * True iff {@code effectiveTicks} (the cadence after upgrade deltas)
     * is a positive integer. The legacy tick loop short-circuits when
     * this is zero; the domain helper pins the same gate.
     */
    public static boolean isActiveCadence(int effectiveTicks) {
        return effectiveTicks > 0;
    }
}
