package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip and shape invariants for the
 * {@link QueueEntry#toIntent(QueueEntry)} /
 * {@link QueueEntry#fromIntent(ConstructionIntent)} boundary added by
 * ADR-0027. The boundary is the only place {@code BlockPos} and
 * {@code worldPosKey} meet — every other call site in the
 * act-5 SUPPLY-mode loop uses the domain or the MC type exclusively,
 * and the boundary converters keep the two shapes in lockstep.
 *
 * <p><b>Scope.</b> The tests below cover the {@code NewBuild} shape only.
 * The {@code Upgrade} shape's boundary path is pinned by
 * {@link TownConstructionQueueSotTest} (which asserts the converters
 * exist with the right signature via reflection — no behavior assertion
 * needs to run), and by the in-game wire path
 * ({@code Town.tryQueueUpgrade} → {@code ConstructionQueue.enqueue} →
 * {@code Town.toNbt} → {@code QueueEntry.fromIntent}). The behavior
 * itself depends on {@code net.minecraft.core.BlockPos.<clinit>},
 * which requires {@code com.mojang.logging.LogUtils} on the test
 * classpath. That library is intentionally <em>not</em> pulled in by
 * the strict-version carve-out in {@code common/build.gradle} — a
 * future MC bump that changes the logging dep version is caught at
 * test time, not masked by adding the dep. A future carve that adds
 * an MC-aware test target (a {@code :neoforge} test source set with its
 * own {@code gradle test} task) is the right place for the full
 * behavior tests, including the {@code BlockPos} round-trip on the
 * Upgrade shape.
 *
 * <p>What this test pins (the NewBuild shape covers the
 * intent-discriminator branch in both converters, which is the part
 * that is most likely to silently break under refactor):
 * <ul>
 *   <li>{@code toIntent} on a NewBuild entry returns a
 *       {@code ConstructionIntent.NewBuild} with the same {@code entryId}
 *       and {@code buildingDefId};</li>
 *   <li>{@code fromIntent} on a NewBuild intent returns a
 *       {@code QueueEntry.NewBuild} with the same fields;</li>
 *   <li>the round-trip through both converters is a value-equal
 *       fixpoint on a NewBuild entry.</li>
 * </ul>
 */
class QueueEntryBridgeTest {

    @Test
    @DisplayName("toIntent on a NewBuild entry returns a ConstructionIntent.NewBuild with the same fields")
    void toIntentNewBuild() {
        QueueEntry.NewBuild entry = new QueueEntry.NewBuild(7L, "burg:house");

        ConstructionIntent intent = QueueEntry.toIntent(entry);

        assertAll(
            () -> assertTrue(intent instanceof ConstructionIntent.NewBuild,
                "a NewBuild entry maps to a NewBuild intent"),
            () -> assertEquals(7L, intent.entryId(),
                "the entry id survives the boundary"),
            () -> assertEquals("burg:house", intent.buildingDefId(),
                "the def id survives the boundary")
        );
    }

    @Test
    @DisplayName("fromIntent decodes a NewBuild intent into the same MC entry shape")
    void fromIntentNewBuild() {
        ConstructionIntent intent = new ConstructionIntent.NewBuild(13L, "burg:mill");

        QueueEntry entry = QueueEntry.fromIntent(intent);

        assertAll(
            () -> assertTrue(entry instanceof QueueEntry.NewBuild,
                "a NewBuild intent maps back to a NewBuild entry"),
            () -> assertEquals(13L, entry.entryId()),
            () -> assertEquals("burg:mill", entry.defId())
        );
    }

    @Test
    @DisplayName("toIntent / fromIntent round-trip preserves every field on the NewBuild shape")
    void roundTripNewBuild() {
        QueueEntry.NewBuild original = new QueueEntry.NewBuild(15L, "burg:barn");

        QueueEntry restored = QueueEntry.fromIntent(QueueEntry.toIntent(original));

        assertEquals(original, restored,
            "NewBuild round-trip is a value-equal fixpoint");
    }

    @Test
    @DisplayName("the boundary converters compose with a ConstructionQueue.enqueue on a NewBuild flow")
    void boundaryComposesWithNewBuildQueue() {
        // This is the path Town.toNbt / fromNbt / constructionQueueView()
        // all use for the NewBuild shape: domain intents flow through
        // fromIntent, get serialized to NBT (not exercised here), get
        // deserialized back to QueueEntry (not exercised here), get
        // converted back to intents, and the resulting domain queue
        // equals the original.
        ConstructionQueue original = ConstructionQueue.EMPTY
            .enqueue(new ConstructionIntent.NewBuild(20L, "burg:a"))
            .enqueue(new ConstructionIntent.NewBuild(21L, "burg:b"));

        java.util.List<ConstructionIntent> restoredIntents = new java.util.ArrayList<>();
        for (ConstructionIntent intent : original.entries()) {
            QueueEntry entry = QueueEntry.fromIntent(intent);
            restoredIntents.add(QueueEntry.toIntent(entry));
        }
        ConstructionQueue restored = ConstructionQueue.of(restoredIntents);

        assertEquals(original.size(), restored.size(),
            "the round-trip through the boundary keeps the same entry count");
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.entries().get(i), restored.entries().get(i),
                "entry " + i + " survives the boundary round-trip");
        }
    }
}
