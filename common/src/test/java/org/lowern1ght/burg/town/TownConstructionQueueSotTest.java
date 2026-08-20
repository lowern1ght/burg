package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signature pin for ADR-0027 — the construction queue flip. The
 * behavior cases that would normally live here (tryAddToConstructionQueue
 * charges stock, removeFromConstructionQueue refunds it, etc.) are
 * pinned by the in-game wire path
 * ({@code TownHubScreenV2} → {@code Town.tryAddToConstructionQueue} →
 * {@code Town.consumeQueueEntry}). A future carve that adds an MC-aware
 * test target (a {@code :neoforge} test source set with its own
 * {@code gradle test} task) is the right place for the full behavior
 * tests.
 *
 * <p>What this test pins (the discipline that makes the dual-write
 * {@code syncConstructionQueueFromLegacy} helper unnecessary):
 * <ol>
 *   <li>{@code Town.constructionQueue} is now a {@link ConstructionQueue}
 *       field, not a {@code List<QueueEntry>} — the domain type is the
 *       SoT, and the MC view is derived on demand by
 *       {@link Town#getConstructionQueue()}.</li>
 *   <li>The dual-write cache field
 *       {@code constructionQueueDomain} and the {@code syncConstructionQueueFromLegacy()}
 *       sync helper no longer exist on {@code Town}. Their
 *       removal is the whole point of the flip.</li>
 *   <li>{@link Town#constructionQueueView()} returns the SoT directly
 *       (a plain field read) — no consistency check, no rebuild
 *       fallback, the "missed a sync" safety net is gone because the
 *       SoT and the cache are the same field now.</li>
 *   <li>The {@code getConstructionQueue()} read path stays MC-typed
 *       ({@code List<QueueEntry>}) so the {@code TownHubDataBuilder}
 *       S2C packet and the {@code SimpleStateMachine} builder NPC
 *       continue to work without an API change — the list is rebuilt
 *       from the domain on demand via {@link QueueEntry#fromIntent}.</li>
 * </ol>
 *
 * <p>The {@code constructionQueueDomain} field is checked for
 * <em>absence</em> via reflection: a regression that re-introduces the
 * dual-write pattern would re-add the field and this test would fail
 * with a clear message pointing at the discipline that should not
 * return.
 */
class TownConstructionQueueSotTest {

    @Test
    @DisplayName("Town.constructionQueue is now a ConstructionQueue field — the domain type is the SoT")
    void primaryStateIsDomain() throws Exception {
        Field field = Town.class.getDeclaredField("constructionQueue");

        assertNotNull(field, "the constructionQueue field must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPrivate(field.getModifiers()),
                "the SoT field stays private (ADR-0027)"),
            () -> assertEquals(ConstructionQueue.class, field.getType(),
                "the SoT field type is the domain ConstructionQueue, not List<QueueEntry>"
                    + " — a regression that flips back to the MC list breaks this assertion"),
            () -> assertTrue(Modifier.isStatic(field.getModifiers()) == false,
                "the SoT field is per-instance (Town state) — not a static cache")
        );
    }

    @Test
    @DisplayName("the dual-write cache field constructionQueueDomain is gone")
    void dualWriteCacheFieldIsGone() {
        NoSuchFieldException thrown = null;
        try {
            Town.class.getDeclaredField("constructionQueueDomain");
        } catch (NoSuchFieldException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "constructionQueueDomain was the dual-write cache field; ADR-0027 removed it."
                + " Its return would re-introduce the racy dual-write pattern.");
    }

    @Test
    @DisplayName("the dual-write sync helper syncConstructionQueueFromLegacy is gone")
    void syncHelperIsGone() {
        NoSuchMethodException thrown = null;
        try {
            Town.class.getDeclaredMethod("syncConstructionQueueFromLegacy");
        } catch (NoSuchMethodException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "syncConstructionQueueFromLegacy was the per-mutation cache rebuild;"
                + " ADR-0027 removed it. Its return would re-introduce the per-mutation"
                + " rebuild cost and the cache-fallback complexity the flip eliminated.");
    }

    @Test
    @DisplayName("the cache consistency check constructionQueueCacheIsConsistent is gone")
    void cacheConsistencyCheckIsGone() {
        NoSuchMethodException thrown = null;
        try {
            Town.class.getDeclaredMethod("constructionQueueCacheIsConsistent");
        } catch (NoSuchMethodException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "constructionQueueCacheIsConsistent was the per-read emptiness check;"
                + " ADR-0027 removed it. Its return would re-introduce the per-read"
                + " check that exists only to catch missed syncs — and there are no"
                + " syncs to miss because the SoT and the cache are the same field now.");
    }

    @Test
    @DisplayName("Town.constructionQueueView() returns ConstructionQueue — the SoT accessor")
    void constructionQueueViewReturnsDomain() throws Exception {
        Method view = Town.class.getMethod("constructionQueueView");

        assertNotNull(view, "the constructionQueueView() accessor must exist");
        assertAll(
            () -> assertTrue(Modifier.isPublic(view.getModifiers()),
                "the accessor is public so application code (act-5 SUPPLY mode,"
                    + " the engine seam, the road planner) can reach the SoT"),
            () -> assertEquals(ConstructionQueue.class, view.getReturnType(),
                "the accessor returns the domain ConstructionQueue — not a derived view,"
                    + " not a cached projection, the SoT itself")
        );
    }

    @Test
    @DisplayName("Town.getConstructionQueue() stays MC-typed for the legacy read path")
    void legacyReadPathStaysMcTyped() throws Exception {
        Method getter = Town.class.getMethod("getConstructionQueue");

        assertNotNull(getter, "the legacy getConstructionQueue() accessor must still exist");
        assertAll(
            () -> assertTrue(Modifier.isPublic(getter.getModifiers())),
            () -> assertEquals(List.class, getter.getReturnType(),
                "the legacy read path returns List<QueueEntry> (raw List at the JVM level"
                    + " via type erasure) so the TownHubDataBuilder S2C packet and the"
                    + " SimpleStateMachine builder NPC keep working without an API change")
        );
    }

    @Test
    @DisplayName("QueueEntry exposes the new MC <-> domain boundary converters")
    void queueEntryExposesBridgeConverters() throws Exception {
        Method toIntent = QueueEntry.class.getMethod("toIntent", QueueEntry.class);
        Method fromIntent = QueueEntry.class.getMethod("fromIntent", ConstructionIntent.class);

        assertAll(
            () -> assertTrue(Modifier.isStatic(toIntent.getModifiers()),
                "toIntent is a static factory — the boundary is a pure conversion"),
            () -> assertTrue(Modifier.isStatic(fromIntent.getModifiers()),
                "fromIntent is a static factory — the boundary is a pure conversion"),
            () -> assertEquals(ConstructionIntent.class, toIntent.getReturnType(),
                "toIntent returns a domain ConstructionIntent"),
            () -> assertEquals(QueueEntry.class, fromIntent.getReturnType(),
                "fromIntent returns the MC QueueEntry shape")
        );
    }
}
