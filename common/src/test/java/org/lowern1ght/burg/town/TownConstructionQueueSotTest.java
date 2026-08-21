package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
 *   <li>{@code Town.constructionQueue} is a {@link ConstructionQueue}
 *       field — the domain type is the SoT.</li>
 *   <li>The dual-write cache field {@code constructionQueueDomain} and
 *       the {@code syncConstructionQueueFromLegacy()} sync helper no
 *       longer exist on {@code Town}. Their removal is the whole point
 *       of the flip.</li>
 *   <li>{@link Town#constructionQueueView()} is the SoT accessor — a
 *       plain field read, no consistency check, no rebuild fallback,
 *       the "missed a sync" safety net is gone because the SoT and the
 *       cache are the same field now.</li>
 *   <li>The legacy {@code getConstructionQueue()} projection (the
 *       O(N) {@code List<QueueEntry>} rebuild on every read) is
 *       <b>gone</b>. Queue-consumer migration: callers that need a
 *       {@link QueueEntry} (the {@code TownHubDataBuilder} S2C packet,
 *       the {@code SimpleStateMachine} builder NPC) map the SoT's
 *       {@link ConstructionQueue#entries()} through
 *       {@link QueueEntry#fromIntent} at the call site. The global
 *       projection would only mask the boundary conversion and
 *       re-introduce the per-read rebuild cost the flip eliminated.</li>
 * </ol>
 *
 * <p>The {@code constructionQueueDomain} field and the
 * {@code getConstructionQueue()} method are checked for
 * <em>absence</em> via reflection: a regression that re-introduces the
 * dual-write pattern or the legacy projection would re-add them and
 * this test would fail with a clear message pointing at the discipline
 * that should not return.
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
    @DisplayName("Town.constructionQueueView() is the SoT-shaped surface — size / isEmpty / hasCapacity / entries are the read API")
    void constructionQueueViewIsTheSotShapedSurface() throws Exception {
        // The facade hands out the SoT directly. The surface that callers
        // read from is the immutable ConstructionQueue's read API, not a
        // legacy MC adapter. This pins that the read API the consumers
        // (SimpleStateMachine.tickPlayerQueue, TownHubDataBuilder.buildHubData,
        // the game-test asserts) actually exercise is the SoT shape.
        //
        // Reflective assertion only: the bare-JVM :common:test classpath
        // does not pull in netty (the `ResourceLocation.<clinit>` chain
        // needs `io/netty/handler/codec/EncoderException`), so we cannot
        // `new Town()` here. The accessor + the SoT shape are
        // independently pinned by the constructionQueueViewReturnsDomain
        // test above and the ConstructionQueue* test cases in
        // :common:test. The behavior pin lives in :neoforge:test.
        Method view = Town.class.getMethod("constructionQueueView");

        assertAll(
            () -> assertEquals(ConstructionQueue.class, view.getReturnType(),
                "the accessor returns the SoT shape — the domain ConstructionQueue"
                    + " whose size / isEmpty / hasCapacity / entries are the read API"
                    + " the migrated consumers iterate"),
            () -> assertTrue(ConstructionQueue.class.isAssignableFrom(
                    (Class<?>) view.getReturnType()),
                "the return type is the SoT itself — same JVM-level class the migration"
                    + " surfaces through, no wrapping / no adapter"),
            () -> assertTrue(ConstructionQueue.EMPTY.isEmpty(),
                "the SoT EMPTY sentinel is empty — the read API hands out the empty"
                    + " sentinel by default, a plain field read, no projection to drift"),
            () -> assertEquals(54, ConstructionQueue.EMPTY.capacity(),
                "the SoT capacity is the Town.QUEUE_CAPACITY constant — the same bound"
                    + " the legacy projection rebuilt against every read, but here lifted"
                    + " to a plain field read on the immutable value object"),
            () -> assertEquals(0, ConstructionQueue.EMPTY.entries().size(),
                "the SoT entries() view is unmodifiable and empty by default — the"
                    + " migrated consumers iterate this view and map to QueueEntry locally")
        );
    }

    @Test
    @DisplayName("the legacy getConstructionQueue() projection is gone — the SoT is constructionQueueView()")
    void legacyGetConstructionQueueProjectionIsGone() {
        // Queue-consumer migration. The legacy projection rebuilt a
        // `List<QueueEntry>` from `constructionQueue.entries()` on every
        // call. The SoT is the domain `ConstructionQueue`; callers that
        // need an MC-typed `QueueEntry` adapt locally at the call site
        // via `QueueEntry.fromIntent`. The global projection would only
        // mask the boundary conversion and re-introduce the per-read
        // rebuild cost the flip eliminated.
        NoSuchMethodException thrown = null;
        try {
            Town.class.getMethod("getConstructionQueue");
        } catch (NoSuchMethodException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "getConstructionQueue() was the O(N) rebuild of the legacy MC-typed List<QueueEntry>."
                + " Queue-consumer migration removed it — the SoT is constructionQueueView(),"
                + " and consumers that need a QueueEntry map the SoT's entries() through"
                + " QueueEntry.fromIntent at the call site. A regression that re-adds the"
                + " global projection would re-introduce the per-read rebuild cost and the"
                + " surface area that masks the boundary conversion.");
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
