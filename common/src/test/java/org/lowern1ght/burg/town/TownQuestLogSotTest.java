package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.QuestLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signature / discipline pin for ADR-0028 — the quest log SoT flip.
 *
 * <p>The behavior cases that would normally live here
 * ({@code addQuest} adds a STATUS_ACTIVE ref to the SoT,
 * {@code removeQuest} drops it, {@code stampQuestCompletion} appends a
 * STATUS_COMPLETED ref + tick, {@code cleanupOrphanedQuestData} prunes
 * both the roll and the completion map) are pinned by the
 * {@link org.lowern1ght.burg.domain.settlement.QuestLogTest} /
 * {@link org.lowern1ght.burg.domain.settlement.QuestLogMutationTest}
 * suite on the value object the {@code Town} facade mutates, plus the
 * simulation tests in {@code QuestLogTownFlowTest}. A future carve
 * that adds an MC-aware test target (a {@code :neoforge} test source
 * set with its own {@code gradle test} task) is the right place for
 * the full behavior tests against {@code new Town()}.
 *
 * <p>What this test pins (the discipline that makes the dual-write
 * helper unnecessary):
 * <ol>
 *   <li>{@code Town.questLog} is now a {@link QuestLog} field — the
 *       domain type is the SoT, and the MC legacy list
 *       ({@code List<Quest> activeQuests}) and the legacy completion
 *       map ({@code Map<String, Long> questDefLastCompleted}) are
 *       gone.</li>
 *   <li>The dual-write cache field {@code questLogDomain} and the
 *       sync helpers {@code syncQuestLogFromLegacy} +
 *       {@code questLogCacheIsConsistent} no longer exist on
 *       {@code Town}. Their removal is the whole point of the flip —
 *       the SoT and the projection are the same field, so there is
 *       no cache to keep in sync and no consistency check to
 *       maintain.</li>
 *   <li>{@link Town#questLog()} returns the SoT directly (a plain
 *       field read) — no rebuild fallback, no consistency check.</li>
 *   <li>The legacy MC read path {@link Town#getActiveQuests()} and
 *       {@link Town#getQuestDefLastCompleted()} stay MC-typed so the
 *       {@code TickScheduler.tickQuests} /
 *       {@code QuestManager.isAlreadyActive(def, List<Quest>)} /
 *       {@code TownHubDataBuilder.buildQuestsTag} /
 *       {@code C2SContributeQuestPacket.handle} consumers continue
 *       to work without an API change.</li>
 *   <li>The four mutators
 *       ({@code addQuest}, {@code removeQuest},
 *       {@code stampQuestCompletion},
 *       {@code cleanupOrphanedQuestData}) keep their public
 *       signatures so the existing call sites compile.</li>
 * </ol>
 */
class TownQuestLogSotTest {

    @Test
    @DisplayName("Town.questLog is now a QuestLog field — the domain type is the SoT")
    void primaryStateIsDomain() throws Exception {
        Field field = Town.class.getDeclaredField("questLog");

        assertNotNull(field, "the questLog field must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPrivate(field.getModifiers()),
                "the SoT field stays private (ADR-0028)"),
            () -> assertEquals(QuestLog.class, field.getType(),
                "the SoT field type is the domain QuestLog, not List<Quest> or Map<String, Long>"
                    + " — a regression that flips back to the MC list breaks this assertion"),
            () -> assertTrue(Modifier.isStatic(field.getModifiers()) == false,
                "the SoT field is per-instance (Town state) — not a static cache")
        );
    }

    @Test
    @DisplayName("the legacy MC activeQuests List<Quest> field is gone")
    void legacyActiveQuestsFieldIsGone() {
        NoSuchFieldException thrown = null;
        try {
            Town.class.getDeclaredField("activeQuests");
        } catch (NoSuchFieldException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "activeQuests was the legacy MC list SoT before ADR-0028."
                + " Its return would re-introduce the dual-write pattern the flip eliminated.");
    }

    @Test
    @DisplayName("the legacy MC questDefLastCompleted Map<String, Long> field is gone")
    void legacyCompletionMapFieldIsGone() {
        NoSuchFieldException thrown = null;
        try {
            Town.class.getDeclaredField("questDefLastCompleted");
        } catch (NoSuchFieldException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "questDefLastCompleted was the legacy MC completion map SoT before ADR-0028."
                + " Its return would re-introduce the dual-write pattern the flip eliminated.");
    }

    @Test
    @DisplayName("the dual-write cache field questLogDomain is gone")
    void dualWriteCacheFieldIsGone() {
        NoSuchFieldException thrown = null;
        try {
            Town.class.getDeclaredField("questLogDomain");
        } catch (NoSuchFieldException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "questLogDomain was the dual-write cache field; ADR-0028 removed it."
                + " Its return would re-introduce the racy dual-write pattern.");
    }

    @Test
    @DisplayName("the dual-write sync helper syncQuestLogFromLegacy is gone")
    void syncHelperIsGone() {
        NoSuchMethodException thrown = null;
        try {
            Town.class.getDeclaredMethod("syncQuestLogFromLegacy");
        } catch (NoSuchMethodException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "syncQuestLogFromLegacy was the per-mutation cache rebuild; ADR-0028"
                + " removed it. Its return would re-introduce the per-mutation rebuild"
                + " cost and the cache-fallback complexity the flip eliminated.");
    }

    @Test
    @DisplayName("the cache consistency check questLogCacheIsConsistent is gone")
    void cacheConsistencyCheckIsGone() {
        NoSuchMethodException thrown = null;
        try {
            Town.class.getDeclaredMethod("questLogCacheIsConsistent");
        } catch (NoSuchMethodException expected) {
            thrown = expected;
        }
        assertNotNull(thrown,
            "questLogCacheIsConsistent was the per-read emptiness check; ADR-0028"
                + " removed it. Its return would re-introduce the per-read check that"
                + " exists only to catch missed syncs — and there are no syncs to miss"
                + " because the SoT and the cache are the same field now.");
    }

    @Test
    @DisplayName("Town.questLog() returns QuestLog — the SoT accessor")
    void questLogAccessorReturnsDomain() throws Exception {
        Method view = Town.class.getMethod("questLog");

        assertNotNull(view, "the questLog() accessor must exist");
        assertAll(
            () -> assertTrue(Modifier.isPublic(view.getModifiers()),
                "the accessor is public so application code can reach the SoT"),
            () -> assertEquals(QuestLog.class, view.getReturnType(),
                "the accessor returns the domain QuestLog — not a derived view,"
                    + " not a cached projection, the SoT itself")
        );
    }

    @Test
    @DisplayName("Town.getActiveQuests() stays MC-typed for the engine tick")
    void legacyActiveQuestsReadPathStaysMcTyped() throws Exception {
        Method getter = Town.class.getMethod("getActiveQuests");

        assertNotNull(getter, "the legacy getActiveQuests() accessor must still exist");
        assertAll(
            () -> assertTrue(Modifier.isPublic(getter.getModifiers())),
            () -> assertEquals(List.class, getter.getReturnType(),
                "the legacy read path returns List<Quest> (raw List at the JVM level"
                    + " via type erasure) so TickScheduler.tickQuests, QuestManager.isAlreadyActive,"
                    + " TownHubDataBuilder.buildQuestsTag, and C2SContributeQuestPacket.handle"
                    + " keep working without an API change")
        );
    }

    @Test
    @DisplayName("Town.getQuestDefLastCompleted() stays MC-typed for the legacy reader")
    void legacyCompletionMapReadPathStaysMcTyped() throws Exception {
        Method getter = Town.class.getMethod("getQuestDefLastCompleted");

        assertNotNull(getter, "the legacy getQuestDefLastCompleted() accessor must still exist");
        assertAll(
            () -> assertTrue(Modifier.isPublic(getter.getModifiers())),
            () -> assertEquals(Map.class, getter.getReturnType(),
                "the legacy read path returns Map<String, Long> (raw Map at the JVM level"
                    + " via type erasure) so Settlers.tick and the in-game HUD keep working")
        );
    }

    @Test
    @DisplayName("the four quest-log mutators keep their public signatures")
    void mutatorsKeepPublicSignatures() throws Exception {
        assertAll(
            () -> assertNotNull(Town.class.getMethod("addQuest", Quest.class),
                "addQuest(Quest) is the entry point the engine tick calls"),
            () -> assertNotNull(Town.class.getMethod("removeQuest", String.class),
                "removeQuest(String questId) is the entry point C2SContributeQuestPacket calls"),
            () -> assertNotNull(Town.class.getMethod("stampQuestCompletion", String.class, long.class),
                "stampQuestCompletion(String, long) is the sanctioned write path"
                    + " into the completion map"),
            () -> assertNotNull(Town.class.getMethod("cleanupOrphanedQuestData", Set.class),
                "cleanupOrphanedQuestData(Set<String>) is called by LevelTowns at world load")
        );
    }
}
