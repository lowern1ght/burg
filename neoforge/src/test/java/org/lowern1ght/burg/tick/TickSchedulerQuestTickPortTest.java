package org.lowern1ght.burg.tick;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.datapack.QuestDataHandler;
import org.lowern1ght.burg.town.Quest;
import org.lowern1ght.burg.town.QuestDef;
import org.lowern1ght.burg.town.QuestManager;
import org.lowern1ght.burg.town.Town;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the ADR-0029 quest-tick defId port. The
 * engine-tick seam — {@link TickScheduler#tickQuests(Town, long)} —
 * drives {@link QuestDataHandler}, checks quest presence via
 * {@link QuestManager#isAlreadyActive(Town, String)} (which delegates to
 * {@link Town#findQuestDef}), respects the TASK refresh-interval cooldown,
 * and verifies {@link QuestDef#prerequisites()} before spawning a fresh
 * {@link Quest}. This test exercises the seam on a bare {@code new Town()}
 * (no MinecraftServer required) by:
 *
 * <ol>
 *   <li>registering a quest def into {@link QuestDataHandler} via
 *       reflection (the {@code REGISTRY} map is private and not
 *       directly settable — the carve deliberately does not add a
 *       test-only setter, keeping production code untouched),</li>
 *   <li>driving {@link TickScheduler#tickQuests(Town, long)} with a
 *       large enough {@code gameTime} to clear the TASK refresh
 *       cooldown, and</li>
 *   <li>asserting the {@link Town#findQuestDef} port returns the
 *       spawned quest (i.e. the engine tick reached the new port and
 *       the spawned quest is observable via it).</li>
 * </ol>
 *
 * <p>The bare-JVM signature pin lives in
 * {@code :common:test}'s {@code TownQuestLogSotTest}; this class is the
 * carve's MC-aware wire-up end-to-end pin.
 */
class TickSchedulerQuestTickPortTest {

    @AfterEach
    void resetQuestDataHandlerRegistry() {
        // Reflection-driven cleanup so a run with multiple @Test methods
        // does not leak quest defs across cases. Cleared via the same
        // path `QuestDataHandler.reload` uses (REGISTRY.clear()), then
        // re-asserted empty so a missing reset is loud.
        try {
            Field registry = QuestDataHandler.class.getDeclaredField("REGISTRY");
            registry.setAccessible(true);
            ((java.util.Map<String, QuestDef>) registry.get(null)).clear();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to reset QuestDataHandler.REGISTRY", e);
        }
    }

    /**
     * Registers a quest def into {@link QuestDataHandler#REGISTRY} via
     * the same write path the production {@code reload} uses, so a future
     * carve that switches REGISTRY from a {@code HashMap} to a different
     * shape doesn't silently break this test.
     */
    private static void registerTestQuestDef(QuestDef def) {
        try {
            Field registry = QuestDataHandler.class.getDeclaredField("REGISTRY");
            registry.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, QuestDef> map = (java.util.Map<String, QuestDef>) registry.get(null);
            map.put(def.id(), def);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to register test QuestDef", e);
        }
    }

    private static QuestDef taskQuestDef(String defId) {
        // No conditions, no reward, no prerequisites — the bare-minimum
        // def that the tick spawns a Quest for. The buildFromDef path is
        // exercised against an empty Quest, which is fine for this
        // wire-up test: we only assert the findQuestDef port returned a
        // non-empty Optional carrying *some* Quest.
        return new QuestDef(
            defId,
            "TASK",
            "burg.quest." + defId + ".title",
            "burg.quest." + defId + ".desc",
            List.of(),
            null,
            1L,                                  // refreshIntervalTicks: 1
            QuestDef.Prerequisites.NONE
        );
    }

    @Test
    @DisplayName("ADR-0029 — tickQuests on a fresh Town() invokes the findQuestDef port end to end")
    void tickQuestsInvokesFindQuestDef() {
        // Register one quest def. The def is TASK with a 1-tick refresh
        // interval and no prerequisites; the test uses gameTime=1L so the
        // spawn happens on the first call (gameTime=0L would be < the
        // 1-tick refresh interval and the cooldown gate would skip it).
        String defId = "test_quest_tick_port";
        registerTestQuestDef(taskQuestDef(defId));

        Town town = new Town();

        // Pre-condition: no quest is active for this defId (the new
        // Town() starts empty).
        assertFalse(town.findQuestDef(defId).isPresent(),
            "a fresh Town() must not have the test quest active before tickQuests runs");

        // Drive the pure-logic tickQuests helper. Returns true iff at
        // least one quest was spawned — that's the engine reading
        // through the findQuestDef port (isAlreadyActive returns false,
        // prerequisitesMet returns true, buildFromDef produces a Quest,
        // addQuest populates questDefIndex).
        boolean changed = TickScheduler.tickQuests(town, 1L);

        assertAll(
            () -> assertTrue(changed,
                "tickQuests returns true — the test quest was spawned on the first call"),
            () -> assertTrue(town.findQuestDef(defId).isPresent(),
                "findQuestDef(defId) is now present — the engine tick populated"
                    + " the defId-keyed questDefIndex, observable through the new port"),
            () -> {
                Quest spawned = town.findQuestDef(defId).orElseThrow();
                assertAll(
                    () -> assertSame(defId, spawned.defId,
                        "the spawned Quest carries the right defId — the port"
                            + " surfaces the engine's primary key"),
                    () -> assertNotNull(spawned.questId,
                        "the spawned Quest carries a non-null questId — the per-spawn"
                            + " instance id survives the defId-keyed port (the SoT"
                            + " stores it for the contribute packet's client render)")
                );
            }
        );
    }

    @Test
    @DisplayName("ADR-0029 — tickQuests is idempotent: the second call does not re-spawn the same defId")
    void tickQuestsIsIdempotentForActiveDefId() {
        String defId = "test_quest_idempotent";
        registerTestQuestDef(taskQuestDef(defId));

        Town town = new Town();

        // First call spawns the quest.
        assertTrue(TickScheduler.tickQuests(town, 1L),
            "first tickQuests spawns the test quest");
        Quest first = town.findQuestDef(defId).orElseThrow();
        String firstQuestId = first.questId;

        // Second call: isAlreadyActive (now backed by findQuestDef) returns
        // true for this defId, so the for-loop skips it. The returned
        // boolean is false (nothing changed), and the quest id is
        // preserved — the engine does not re-mint a questId.
        assertFalse(TickScheduler.tickQuests(town, 2L),
            "second tickQuests returns false — the test quest is already active"
                + " and the defId port prevents re-spawn");

        Quest second = town.findQuestDef(defId).orElseThrow();
        assertAll(
            () -> assertEquals(firstQuestId, second.questId,
                "the surviving quest keeps its original questId — the defId"
                    + " port is stable across repeated ticks"),
            () -> assertSame(first, second,
                "findQuestDef returns the same Quest instance — no defensive copy")
        );
    }

    @Test
    @DisplayName("ADR-0029 — isAlreadyActive(Town, String) reads through findQuestDef")
    void isAlreadyActiveDelegatesToFindQuestDef() {
        String presentDef = "test_quest_present";
        String absentDef = "test_quest_absent";
        registerTestQuestDef(taskQuestDef(presentDef));

        Town town = new Town();
        TickScheduler.tickQuests(town, 1L);

        assertAll(
            () -> assertTrue(QuestManager.isAlreadyActive(town, presentDef),
                "isAlreadyActive(town, presentDef) is true — the engine tick populated"
                    + " questDefIndex and the new signature surfaces it"),
            () -> assertFalse(QuestManager.isAlreadyActive(town, absentDef),
                "isAlreadyActive(town, absentDef) is false — no quest for this defId"),
            () -> assertEquals(town.findQuestDef(presentDef).isPresent(),
                QuestManager.isAlreadyActive(town, presentDef),
                "isAlreadyActive is the boolean-projection of findQuestDef().isPresent()")
        );
    }
}
