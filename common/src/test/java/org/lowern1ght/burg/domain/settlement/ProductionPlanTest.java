package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure-arithmetic production tick, on a bare JVM.
 *
 * <p>The carve the test pins (ADR-0015): {@code ProductionManager.tick}
 * routes its per-entry amount calculation through
 * {@code ProductionPlan.computeDueOutputs}, so the arithmetic of
 * "what did this building produce this tick" is exercisable without
 * a Minecraft classpath. These tests assert the contract the tick
 * loop now relies on.
 */
class ProductionPlanTest {

    private static ItemId item(String raw) {
        return ItemId.of(raw);
    }

    private static final ItemId OAK_LOG = item("minecraft:oak_log");
    private static final ItemId STONE = item("minecraft:stone");
    private static final ItemId WHEAT = item("minecraft:wheat");

    @Test
    @DisplayName("ProductionRule rejects non-positive amount / cadence / negative capacity")
    void productionRuleValidates() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(OAK_LOG, 0, 100, 64),
                "amount must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(OAK_LOG, -1, 100, 64),
                "amount must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(OAK_LOG, 5, 0, 64),
                "everyTicks must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(OAK_LOG, 5, -1, 64),
                "everyTicks must be positive"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> new ProductionRule(OAK_LOG, 5, 100, -1),
                "capacity must be non-negative")
        );
    }

    @Test
    @DisplayName("ProductionRule.isDue ticks on the everyTicks boundary from tick 0")
    void productionRuleIsDue() {
        ProductionRule rule = new ProductionRule(OAK_LOG, 1, 100, 64);
        assertAll(
            () -> assertTrue(rule.isDue(0L), "tick 0 is the first due tick"),
            () -> assertTrue(rule.isDue(100L)),
            () -> assertTrue(rule.isDue(200L)),
            () -> assertFalse(rule.isDue(50L), "between boundaries the rule is not due"),
            () -> assertFalse(rule.isDue(99L)),
            () -> assertFalse(rule.isDue(101L))
        );
    }

    @Test
    @DisplayName("ProductionRule.isActiveCadence mirrors the legacy everyTicks > 0 short-circuit")
    void productionRuleIsActiveCadence() {
        assertAll(
            () -> assertTrue(ProductionRule.isActiveCadence(100)),
            () -> assertTrue(ProductionRule.isActiveCadence(1)),
            () -> assertFalse(ProductionRule.isActiveCadence(0),
                "the legacy tick loop skips entries with effectiveTicks <= 0"),
            () -> assertFalse(ProductionRule.isActiveCadence(-1))
        );
    }

    @Test
    @DisplayName("an empty plan produces no outputs at any tick")
    void emptyPlanProducesNothing() {
        ProductionPlan plan = ProductionPlan.EMPTY;
        assertAll(
            () -> assertTrue(plan.isEmpty()),
            () -> assertSame(ProductionPlan.EMPTY, plan,
                "EMPTY is referentially stable")
        );
        for (long tick : new long[]{0L, 1L, 100L, 9_999L}) {
            assertEquals(Map.of(), plan.computeDueOutputs(tick, 0L),
                "an empty plan emits no outputs at tick " + tick);
        }
    }

    @Test
    @DisplayName("a non-zero bonus on a single rule emits the scaled amount on its due tick")
    void singleRuleDue() {
        ProductionRule rule = new ProductionRule(OAK_LOG, 5, 100, 320);
        ProductionPlan plan = new ProductionPlan(List.of(rule), 1.0);

        assertEquals(Map.of(OAK_LOG, 5), plan.computeDueOutputs(0L, 0L));
        assertEquals(Map.of(), plan.computeDueOutputs(50L, 0L),
            "a non-due tick emits nothing");
        assertEquals(Map.of(OAK_LOG, 5), plan.computeDueOutputs(100L, 100L));
    }

    @Test
    @DisplayName("the bonus multiplier scales the per-tick amount before rounding")
    void bonusMultiplierScales() {
        ProductionRule rule = new ProductionRule(STONE, 4, 50, 200);
        // 4 * 1.5 = 6
        ProductionPlan plan = new ProductionPlan(List.of(rule), 1.5);
        assertEquals(Map.of(STONE, 6), plan.computeDueOutputs(0L, 0L));
    }

    @Test
    @DisplayName("a zero bonus multiplier collapses the plan to no output; a negative bonus is rejected at construction")
    void zeroBonusCollapses() {
        ProductionRule rule = new ProductionRule(WHEAT, 3, 20, 100);
        ProductionPlan zero = new ProductionPlan(List.of(rule), 0.0);

        assertEquals(Map.of(), zero.computeDueOutputs(0L, 0L),
            "bonusMultiplier = 0.0 produces nothing");

        // The constructor rejects a negative bonus; the rejection IS the
        // collapse for negative inputs — a plan with a negative bonus
        // cannot exist. computeDueOutputs is also defensive against a
        // bonusMultiplier that turns non-positive post-construction.
        assertThrows(IllegalArgumentException.class,
            () -> new ProductionPlan(List.of(rule), -0.5),
            "the constructor rejects a negative bonus");
    }

    @Test
    @DisplayName("multiple rules in one plan emit their scaled amounts independently")
    void multipleRules() {
        ProductionRule oak = new ProductionRule(OAK_LOG, 2, 100, 320);
        ProductionRule stone = new ProductionRule(STONE, 5, 50, 200);
        ProductionPlan plan = new ProductionPlan(List.of(oak, stone), 1.0);

        // tick 0: oak is due, stone is due.
        assertEquals(
            Map.of(OAK_LOG, 2, STONE, 5),
            plan.computeDueOutputs(0L, 0L)
        );
        // tick 50: stone is due, oak is not.
        assertEquals(
            Map.of(STONE, 5),
            plan.computeDueOutputs(50L, 0L)
        );
        // tick 100: both are due again.
        assertEquals(
            Map.of(OAK_LOG, 2, STONE, 5),
            plan.computeDueOutputs(100L, 0L)
        );
    }

    @Test
    @DisplayName("overlapping rules on the same item sum into one map entry")
    void overlappingRulesSum() {
        // Two rules that both fire on tick 0 and produce OAK_LOG — the
        // plan should merge them into a single map entry.
        ProductionRule slow = new ProductionRule(OAK_LOG, 2, 100, 320);
        ProductionRule fast = new ProductionRule(OAK_LOG, 3, 100, 320);
        ProductionPlan plan = new ProductionPlan(List.of(slow, fast), 1.0);

        assertEquals(Map.of(OAK_LOG, 5), plan.computeDueOutputs(0L, 0L));
    }

    @Test
    @DisplayName("a scaled amount that rounds to zero is dropped, not added as a zero entry")
    void roundedZeroIsDropped() {
        // amount 1, multiplier 0.4 -> 0.4 -> Math.round -> 0
        ProductionRule rule = new ProductionRule(OAK_LOG, 1, 100, 320);
        ProductionPlan plan = new ProductionPlan(List.of(rule), 0.4);

        Map<ItemId, Integer> out = plan.computeDueOutputs(0L, 0L);
        assertTrue(out.isEmpty(),
            "a scaled amount that rounds to zero is dropped — the map is sparse");
    }

    @Test
    @DisplayName("rules() and bonusMultiplier() are read-only views")
    void viewsAreReadOnly() {
        ProductionRule rule = new ProductionRule(OAK_LOG, 1, 100, 64);
        ProductionPlan plan = new ProductionPlan(List.of(rule), 1.5);

        assertAll(
            () -> assertEquals(1, plan.rules().size()),
            () -> assertEquals(rule, plan.rules().get(0)),
            () -> assertEquals(1.5, plan.bonusMultiplier()),
            () -> assertThrows(UnsupportedOperationException.class,
                () -> plan.rules().add(rule),
                "rules() returns an unmodifiable view")
        );
    }

    @Test
    @DisplayName("computeDueOutputs is referentially stable on a no-output tick")
    void noOutputTickIsEmptySentinel() {
        ProductionRule rule = new ProductionRule(OAK_LOG, 1, 100, 64);
        ProductionPlan plan = new ProductionPlan(List.of(rule), 1.0);

        Map<ItemId, Integer> empty = plan.computeDueOutputs(50L, 0L);
        assertNotNull(empty);
        assertTrue(empty.isEmpty(),
            "a tick that produces nothing returns the empty map, not null");
    }
}
