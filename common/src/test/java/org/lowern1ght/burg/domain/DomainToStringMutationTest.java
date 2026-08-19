package org.lowern1ght.burg.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.diplomacy.RelationStance;
import org.lowern1ght.burg.domain.realm.AutonomyBand;
import org.lowern1ght.burg.domain.realm.HoldingKind;
import org.lowern1ght.burg.domain.realm.RealmId;
import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;
import org.lowern1ght.burg.domain.settlement.ProductionPlan;
import org.lowern1ght.burg.domain.settlement.ProductionRule;
import org.lowern1ght.burg.domain.settlement.QuestLog;
import org.lowern1ght.burg.domain.settlement.QuestRef;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.settlement.StandingBook;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.settlement.TransformationRule;
import org.lowern1ght.burg.domain.shared.CitizenId;
import org.lowern1ght.burg.domain.shared.ItemId;
import org.lowern1ght.burg.domain.war.BattleOutcome;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * One smoke invariant across the whole domain surface: {@code toString}
 * on every domain type — empty sentinels and populated instances alike —
 * completes without throwing and yields a non-null string. A hand-written
 * {@code toString} that dereferences a nullable field dies here even
 * when every behavioural test passes.
 */
class DomainToStringMutationTest {

    private static String show(Object value) {
        String text = value.toString();
        assertNotNull(text, "toString must return a string for " + value.getClass().getSimpleName());
        return text;
    }

    @Test
    @DisplayName("toString on every domain type is non-throwing and non-null")
    void toStringIsSafeEverywhere() {
        ItemId stone = ItemId.of("minecraft:stone");
        ItemId wheat = ItemId.of("minecraft:wheat");
        ItemId flour = ItemId.of("minecraft:flour");
        CitizenId alice = CitizenId.of(UUID.nameUUIDFromBytes("alice".getBytes()));

        assertDoesNotThrow(() -> {
            // shared
            show(ItemId.EMPTY);
            show(stone);
            show(CitizenId.EMPTY);
            show(alice);
            // settlement
            for (Acquisition value : Acquisition.values()) {
                show(value);
            }
            show(Standing.ZERO);
            show(new Standing(alice, -5));
            show(StandingBook.EMPTY);
            show(StandingBook.EMPTY.set(alice, 7));
            show(StockLedger.EMPTY);
            show(StockLedger.EMPTY.add(stone, 10).take(stone, 3));
            show(new ConstructionIntent.NewBuild(1, "burg:house"));
            show(new ConstructionIntent.Upgrade(2, "burg:house", "123456", 1));
            show(ConstructionQueue.EMPTY);
            show(ConstructionQueue.of(List.of(
                new ConstructionIntent.NewBuild(1, "burg:house"),
                new ConstructionIntent.Upgrade(2, "burg:house", "123456", 1))));
            show(QuestLog.EMPTY);
            show(QuestLog.EMPTY
                .withAdded(QuestRef.of("burg:fetch", QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE))
                .withCompleted("burg:old", 42L));
            show(QuestRef.ofUnstatused("burg:lore", QuestRef.TYPE_NOTE));
            show(new ProductionRule(stone, 4, 100L, 64));
            show(ProductionPlan.EMPTY);
            show(new ProductionPlan(List.of(new ProductionRule(stone, 4, 100L, 64)), 1.5));
            show(new TransformationRule.StockCost(wheat, 3));
            show(new TransformationRule(
                List.of(new TransformationRule.StockCost(wheat, 3)), flour, 1, 64));
            // realm / diplomacy / war
            show(RealmId.of("nord"));
            for (HoldingKind kind : HoldingKind.values()) {
                show(kind);
            }
            for (AutonomyBand band : AutonomyBand.values()) {
                show(band);
            }
            for (RelationStance stance : RelationStance.values()) {
                show(stance);
            }
            show(BattleOutcome.decided(true));
            show(BattleOutcome.counted(false, 3, 7));
            // populated map / list views ride along inside the shells above
            show(Map.of(stone, 10));
        }, "toString must be safe for every domain shape, empty and populated");
    }
}
