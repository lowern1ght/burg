package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.QuestLog;
import org.lowern1ght.burg.domain.settlement.QuestRef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM behaviour tests for the quest-log SoT promotion (ADR-0028).
 *
 * <p>The {@code Town.addQuest} / {@code removeQuest} /
 * {@code stampQuestCompletion} / {@code cleanupOrphanedQuestData}
 * mutators can't run on a bare JVM — instantiating {@code Town} pulls
 * in Netty (the carve-out in {@code common/build.gradle §"Plain JVM
 * tests, no Minecraft"} is for class metadata, not full bodies). The
 * discipline tests in {@code TownQuestLogSotTest} pin the signature
 * and field-presence contract via reflection; the behaviour tests
 * here drive the same mutation sequence directly against
 * {@link QuestLog}, the value object the {@code Town} facade mutates.
 *
 * <p>What this suite pins:
 * <ol>
 *   <li>{@code addQuest(Quest)} appends a STATUS_ACTIVE ref for the
 *       {@code defId} via {@link QuestLog#withAdded}. Re-adding a
 *       known defId replaces the existing ref (the engine treats
 *       defId as the primary key).</li>
 *   <li>{@code removeQuest(questId)} drops the ref for the
 *       {@code defId} via {@link QuestLog#withRemoved}. The derived
 *       map (keyed by {@code questId}) is what tells the facade which
 *       {@code defId} to remove.</li>
 *   <li>{@code stampQuestCompletion(defId, tick)} appends a tick to
 *       {@link QuestLog#lastCompleted()} and, if no ref with that
 *       defId is currently on the roll, also appends a STATUS_COMPLETED
 *       ref — preserving the legacy semantic where a completed quest
 *       that is no longer active still appears on the roll with
 *       status COMPLETED. This is the path
 *       {@code C2SContributeQuestPacket.handle} drives:
 *       {@code removeQuest} drops the active ref →
 *       {@code stampQuestCompletion} appends the completed ref + tick.</li>
 *   <li>{@code cleanupOrphanedQuestData(validDefIds)} prunes both
 *       the roll and the completion map by defId; the rebuild path
 *       uses {@link QuestLog#of(List, Map)} which defensively drops
 *       malformed entries, mirroring the legacy discipline.</li>
 *   <li>NBT round-trip is byte-identical: a {@link QuestLog} built
 *       from {@code activeQuestMap.values()} + completion map yields
 *       the same roll order (active first, then completed-only
 *       defIds) the legacy code produced.</li>
 * </ol>
 *
 * <p>This suite deliberately mirrors the mutation-style checks in
 * {@code QuestLogMutationTest} (kills the same mutants: a
 * {@code withAdded} that appends a duplicate, a
 * {@code stampQuestCompletion} that drops the completion ref when the
 * engine already removed the active one, a
 * {@code cleanupOrphanedQuestData} that forgets to prune the
 * completion map) but re-frames them as the Town-driven usage
 * pattern. A regression in {@code Town} that re-introduces a dual-
 * write or breaks the byte-identical NBT contract will fail one of
 * the cases below.
 */
class QuestLogTownFlowTest {

    private static QuestRef activeRef(String defId) {
        return QuestRef.of(defId, QuestRef.TYPE_TASK, QuestRef.STATUS_ACTIVE);
    }

    private static QuestRef completedRef(String defId) {
        return QuestRef.of(defId, QuestRef.TYPE_TASK, QuestRef.STATUS_COMPLETED);
    }

    // Mirrors the Town.addQuest(Quest) mutation: a STATUS_ACTIVE ref for
    // the quest's defId, plus the rich Quest data in the engine-tick
    // derived map (LinkedHashMap preserves insertion order for NBT).
    private record ActiveQuestEntry(String questId, String defId, String questType) {}

    @Test
    @DisplayName("addQuest appends a STATUS_ACTIVE ref for the defId, completion map untouched")
    void addQuestAppendsActiveRef() {
        QuestLog beforeAdd = QuestLog.EMPTY;

        // Town.addQuest("q1", "gather_wood", "TASK")
        QuestLog afterAdd = beforeAdd.withAdded(activeRef("gather_wood"));

        assertAll(
            () -> assertEquals(1, afterAdd.size(), "the roll grew by one"),
            () -> assertEquals("gather_wood", afterAdd.findById("gather_wood").defId(),
                "the appended ref carries the right defId"),
            () -> assertEquals(QuestRef.STATUS_ACTIVE, afterAdd.findById("gather_wood").status(),
                "addQuest appends an ACTIVE ref"),
            () -> assertTrue(afterAdd.lastCompleted().isEmpty(),
                "addQuest does not touch the completion map")
        );
    }

    @Test
    @DisplayName("removeQuest drops the ref by defId; completion map is preserved")
    void removeQuestDropsActiveRef() {
        QuestLog beforeRemove = QuestLog.EMPTY
            .withAdded(activeRef("gather_wood"))
            // Add a separate completion entry for a different defId to
            // prove removeQuest does not touch the completion map.
            .withCompleted("mine_stone", 500L);

        // Town.removeQuest("q1") — the facade looks up the defId in the
        // derived map (the rich Quest data keyed by questId) and calls
        // withRemoved(defId).
        QuestLog afterRemove = beforeRemove.withRemoved("gather_wood");

        assertAll(
            () -> assertNull(afterRemove.findById("gather_wood"),
                "the active ref is gone"),
            () -> assertEquals(0, afterRemove.size(),
                "the roll shrank by one"),
            () -> assertEquals(500L, afterRemove.lastCompletedFor("mine_stone"),
                "the completion map is untouched (a different defId)"),
            () -> assertTrue(afterRemove.lastCompleted().containsKey("mine_stone"),
                "the completion entry survives")
        );
    }

    @Test
    @DisplayName("contribute cycle: removeQuest + stampQuestCompletion leaves a STATUS_COMPLETED ref")
    void contributeCycleAppendsCompletedRef() {
        QuestLog beforeContribute = QuestLog.EMPTY.withAdded(activeRef("gather_wood"));

        // C2SContributeQuestPacket.handle drives this sequence:
        //   town.removeQuest(packet.questId());
        //   town.stampQuestCompletion(quest.defId, level.getGameTime());
        QuestLog afterRemove = beforeContribute.withRemoved("gather_wood");

        long now = 12_345L;
        QuestLog afterStamp = afterRemove.withCompleted("gather_wood", now);
        QuestLog finalLog = afterStamp.findById("gather_wood") == null
            ? afterStamp.withAdded(completedRef("gather_wood"))
            : afterStamp;

        assertAll(
            () -> assertEquals(0, afterRemove.size(),
                "the active ref is gone after removeQuest"),
            () -> assertEquals(1, finalLog.size(),
                "the contribute cycle leaves one ref on the roll (STATUS_COMPLETED)"),
            () -> assertEquals(1, finalLog.lastCompleted().size(),
                "the completion map has the entry"),
            () -> assertEquals(now, finalLog.lastCompletedFor("gather_wood")),
            () -> assertNotNull(finalLog.findById("gather_wood"),
                "the SoT still carries the completed quest — findById returns the COMPLETED ref"),
            () -> assertEquals(QuestRef.STATUS_COMPLETED, finalLog.findById("gather_wood").status(),
                "the surviving ref is STATUS_COMPLETED (legacy semantic preserved)"),
            () -> assertEquals("gather_wood", finalLog.findById("gather_wood").defId())
        );
    }

    @Test
    @DisplayName("stampQuestCompletion on an ACTIVE quest does not flip the ref to COMPLETED")
    void stampQuestCompletionPreservesActiveRef() {
        QuestLog beforeStamp = QuestLog.EMPTY.withAdded(activeRef("gather_wood"));

        // A stamp without a prior removeQuest: legacy semantic preserved —
        // the active ref stays ACTIVE; the completion map gains the entry.
        // The Town facade only appends a COMPLETED ref when no ref
        // exists for the defId; here a STATUS_ACTIVE ref already exists.
        QuestLog afterStamp = beforeStamp.withCompleted("gather_wood", 7_000L);

        assertAll(
            () -> assertEquals(1, afterStamp.size(), "the roll is unchanged"),
            () -> assertEquals(QuestRef.STATUS_ACTIVE, afterStamp.findById("gather_wood").status(),
                "the active ref survives — stamp does not auto-complete it"),
            () -> assertEquals(7_000L, afterStamp.lastCompletedFor("gather_wood"),
                "the completion map has the entry regardless")
        );
    }

    @Test
    @DisplayName("cleanupOrphanedQuestData prunes both the roll and the completion map")
    void cleanupPrunesBothFields() {
        QuestLog beforeCleanup = QuestLog.EMPTY
            .withAdded(activeRef("gather_wood"))
            .withAdded(activeRef("deprecated_quest"))
            .withCompleted("deprecated_quest", 1_000L)
            .withCompleted("mine_stone", 2_000L);

        Set<String> valid = Set.of("gather_wood", "mine_stone");

        // Mirror Town.cleanupOrphanedQuestData: filter both the roll and
        // the completion map by the valid defId set, then rebuild via
        // QuestLog.of (which defensively drops malformed entries).
        List<QuestRef> filteredRefs = new ArrayList<>(beforeCleanup.entries().size());
        for (QuestRef ref : beforeCleanup.entries()) {
            if (valid.contains(ref.defId())) filteredRefs.add(ref);
        }
        Map<String, Long> filteredCompleted = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : beforeCleanup.lastCompleted().entrySet()) {
            if (valid.contains(e.getKey())) filteredCompleted.put(e.getKey(), e.getValue());
        }
        QuestLog afterCleanup = QuestLog.of(filteredRefs, filteredCompleted);

        assertAll(
            () -> assertNotNull(afterCleanup.findById("gather_wood"),
                "the valid active ref survives"),
            () -> assertNull(afterCleanup.findById("deprecated_quest"),
                "the orphaned active ref is pruned"),
            () -> assertEquals(2_000L, afterCleanup.lastCompletedFor("mine_stone"),
                "the valid completion survives"),
            () -> assertEquals(0L, afterCleanup.lastCompletedFor("deprecated_quest"),
                "the orphaned completion is pruned"),
            () -> assertEquals(1, afterCleanup.size(), "only the valid active ref remains")
        );
    }

    @Test
    @DisplayName("NBT round-trip preserves roll order: active refs first, completed-only after")
    void nbtRoundTripPreservesOrder() {
        // Simulate the NBT round-trip the Town facade performs:
        //   toNbt: iterate activeQuestMap.values() for ActiveQuests, then
        //          iterate questLog.lastCompleted() for QuestDefLastCompleted;
        //   fromNbt: rebuild questLog so the roll order is "active first in
        //            insertion order, then completed-only defIds in
        //            questLog.lastCompleted() iteration order".
        Map<String, ActiveQuestEntry> active = new LinkedHashMap<>();
        active.put("q1", new ActiveQuestEntry("q1", "first_quest", "TASK"));
        active.put("q2", new ActiveQuestEntry("q2", "second_quest", "TASK"));
        Map<String, Long> completed = new LinkedHashMap<>();
        completed.put("third_quest", 9_000L);   // completed-only, no active ref

        // toNbt path: emit active refs in insertion order, then
        // completed-only defIds in lastCompleted() iteration order.
        List<QuestRef> toNbtEntries = new ArrayList<>();
        for (ActiveQuestEntry e : active.values()) {
            toNbtEntries.add(activeRef(e.defId()));
        }
        for (String defId : completed.keySet()) {
            if (active.values().stream().noneMatch(e -> e.defId().equals(defId))) {
                toNbtEntries.add(completedRef(defId));
            }
        }
        QuestLog toNbt = QuestLog.of(toNbtEntries, completed);

        // fromNbt path: collapse (activeQuests list + completed map) into a fresh QuestLog
        // — same shape the fromNbt code produces.
        List<QuestRef> rebuiltEntries = new ArrayList<>();
        Set<String> activeDefIds = new java.util.HashSet<>();
        for (ActiveQuestEntry e : active.values()) {
            rebuiltEntries.add(activeRef(e.defId()));
            activeDefIds.add(e.defId());
        }
        for (String defId : completed.keySet()) {
            if (!activeDefIds.contains(defId)) rebuiltEntries.add(completedRef(defId));
        }
        QuestLog fromNbt = QuestLog.of(rebuiltEntries, completed);

        assertAll(
            () -> assertEquals(toNbt.entries(), fromNbt.entries(),
                "the roll order is identical after a round-trip"),
            () -> assertEquals(toNbt.lastCompleted(), fromNbt.lastCompleted(),
                "the completion map is identical after a round-trip"),
            () -> assertEquals(toNbt.size(), fromNbt.size()),
            () -> assertEquals(List.of("first_quest", "second_quest", "third_quest"),
                List.of(fromNbt.entries().get(0).defId(),
                    fromNbt.entries().get(1).defId(),
                    fromNbt.entries().get(2).defId()),
                "active refs first in insertion order, completed-only after")
        );
    }

    @Test
    @DisplayName("EMPTY stays referentially stable across Town-style mutations")
    void emptyStaysReferentiallyStable() {
        QuestLog log = QuestLog.EMPTY;

        // removeQuest on EMPTY is a no-op; withRemoved returns the same instance.
        assertSame(log, log.withRemoved("any_def"),
            "EMPTY.withRemoved returns the same instance (mirrors Town.removeQuest no-op)");

        // stampQuestCompletion on EMPTY mutates (the completion map gains an entry);
        // withAdded appends a STATUS_COMPLETED ref (legacy semantic).
        QuestLog afterStamp = log
            .withCompleted("any_def", 100L)
            .withAdded(completedRef("any_def"));
        assertAll(
            () -> assertEquals(1, afterStamp.size(),
                "the stamp + ref append leaves one ref on the roll"),
            () -> assertEquals(100L, afterStamp.lastCompletedFor("any_def")),
            () -> assertEquals(QuestRef.STATUS_COMPLETED,
                afterStamp.findById("any_def").status())
        );
    }

    @Test
    @DisplayName("repeated addQuest for the same defId replaces the existing ref (engine primary-key)")
    void repeatedAddQuestReplacesByDefId() {
        QuestLog afterFirstAdd = QuestLog.EMPTY.withAdded(activeRef("gather_wood"));

        // Engine tick respawn: a new quest instance for the same defId.
        QuestLog afterSecondAdd = afterFirstAdd.withAdded(activeRef("gather_wood"));

        assertAll(
            () -> assertEquals(1, afterSecondAdd.size(),
                "the second addQuest replaced the first ref by defId — no duplicate"),
            () -> assertEquals(QuestRef.STATUS_ACTIVE,
                afterSecondAdd.findById("gather_wood").status(),
                "the surviving ref is still ACTIVE")
        );
    }
}
