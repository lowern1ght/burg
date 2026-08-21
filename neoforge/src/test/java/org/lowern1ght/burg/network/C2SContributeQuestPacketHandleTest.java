package org.lowern1ght.burg.network;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.town.Quest;
import org.lowern1ght.burg.town.Town;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behaviour pin for ADR-0029 — the {@code C2SContributeQuestPacket}
 * ↔ {@link Town} integration through the defId-keyed engine port.
 *
 * <p>The {@code :common:test} counterpart
 * {@code C2SContributeQuestPacketWireTest} pins the record-component
 * shape (no {@code questId} on the record, components are exactly
 * {@code (anchorPos, defId)} in that order, with their declared types).
 * The byte-level codec round-trip is the source-of-truth derived from the
 * record-component shape: a future carve that re-adds a {@code questId}
 * component changes the record shape and the {@code :common:test} pin
 * fires before the wire changes. Together the two test files close the
 * loop: the wire carries {@code defId} only, and the engine resolves by
 * {@code defId} only — a regression on either side breaks at least one
 * of these assertions.
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>{@link Town#findQuestDef(String)} is the engine port.</b> A
 *       quest added via {@link Town#addQuest(Quest)} with a known
 *       {@code defId} is observable through {@code findQuestDef(defId)}.
 *       This is the read site the packet's {@code handle} uses (see
 *       {@code C2SContributeQuestPacket.handle}: {@code town.findQuestDef(packet.defId())}).
 *       If a future carve breaks the {@code findQuestDef} port, this
 *       assertion fires before the handle's runtime path silently drops
 *       contributions.</li>
 *   <li><b>{@link Town#removeQuest(String)} takes a {@code defId}.</b>
 *       The handler's {@code removeQuest(packet.defId())} call resolves
 *       the same way. After removal, {@code findQuestDef(defId)} is
 *       empty. A regression that flips {@code removeQuest} back to a
 *       questId-keyed signature would leave the quest in the
 *       {@code questDefIndex} map and this assertion fires.</li>
 *   <li><b>Two quests with the same {@code defId} collapse.</b> The
 *       engine's primary key is {@code defId} (not the per-spawn
 *       {@code questId}). A second {@code addQuest} with the same
 *       {@code defId} but a different {@code questId} swaps the
 *       {@code questDefIndex} entry — the second {@code questId} wins.
 *       This is the discipline that lets the wire carry {@code defId}
 *       alone: a client cannot accidentally contribute the wrong
 *       spawn because the engine never keyed on {@code questId}.</li>
 * </ol>
 *
 * <p><b>Why no codec round-trip here.</b> The byte-level codec test
 * requires a {@link net.minecraft.network.FriendlyByteBuf}, which
 * extends Netty's {@code io.netty.buffer.ByteBuf}. The {@code :neoforge:test}
 * target's runtime classpath injects Netty via the ModDev legacy
 * classpath, but compile-time resolution of {@code FriendlyByteBuf}
 * requires Netty on the compile classpath too — and it isn't there by
 * design (the existing tests deliberately avoid touching Netty types,
 * keeping the {@code :neoforge:test} target a plain JUnit run, not a
 * ModLauncher boot). The wire-shape discipline is therefore pinned at the
 * {@code :common:test} reflection layer, where the record-component
 * shape determines the codec shape byte-for-byte.
 *
 * <p>What this does <i>not</i> pin (and why a future carve does not need
 * to redo it):
 * <ul>
 *   <li>The {@code handle} body's full MC server interaction (player
 *       inventory inspection, town stock push, era broadcast) lives on
 *       the live wire path and is verified by a future
 *       {@code runGameTestServer} carve. This test pins the contract the
 *       handle relies on (the {@code findQuestDef}/{@code removeQuest}
 *       port pair) at the JVM boundary, not the server-bound handle
 *       itself.</li>
 *   <li>The {@code TownQuestLogSotTest} (in {@code :common:test}) pins
 *       the SoT discipline (the dual-write cache field and the legacy
 *       questId-keyed {@code activeQuestMap} are gone, the SoT is
 *       {@code questLog}).</li>
 *   <li>The {@code TickSchedulerQuestTickPortTest} (in
 *       {@code :neoforge:test}) pins the engine-tick side of the same
 *       contract — {@code TickScheduler.tickQuests} drives
 *       {@code findQuestDef} end to end on a {@code new Town()}. This
 *       file is the C2S-handle-side companion, reading through the same
 *       port from a different entry point.</li>
 * </ul>
 */
class C2SContributeQuestPacketHandleTest {

    @Test
    @DisplayName("ADR-0029 — Town.findQuestDef(defId) is the engine port the handler resolves via")
    void townFindQuestDefIsTheEnginePort() {
        String defId = "test_quest_defid_port";
        Quest q = new Quest();
        q.questId = "instance_001";
        q.defId = defId;

        Town town = new Town();
        town.addQuest(q);

        assertAll(
            () -> assertTrue(town.findQuestDef(defId).isPresent(),
                "findQuestDef(defId) returns the just-added quest — the defId"
                    + " port is the engine's primary lookup. The packet's"
                    + " handle (C2SContributeQuestPacket.handle) resolves via"
                    + " this port: town.findQuestDef(packet.defId()). A regression"
                    + " that breaks the port silently drops contributions."),
            () -> assertSame(q, town.findQuestDef(defId).orElseThrow(),
                "findQuestDef returns the same instance — no defensive copy on"
                    + " the read site."),
            () -> assertEquals("instance_001", town.findQuestDef(defId).orElseThrow().questId,
                "the per-spawn questId survives the defId port — the engine"
                    + " keeps it for the S2C hub data the client renders. The"
                    + " C2S wire does not carry it; the engine does not key"
                    + " on it; the S2C hub data and the NBT storage do.")
        );
    }

    @Test
    @DisplayName("ADR-0029 — Town.removeQuest(defId) drops the defId-keyed entry (the handler's exit path)")
    void townRemoveQuestTakesDefId() {
        String defId = "test_quest_remove_port";
        String otherDefId = "test_quest_other";
        Quest q = new Quest();
        q.questId = "instance_to_remove";
        q.defId = defId;
        Quest other = new Quest();
        other.questId = "instance_other";
        other.defId = otherDefId;

        Town town = new Town();
        town.addQuest(q);
        town.addQuest(other);

        assertTrue(town.findQuestDef(defId).isPresent(),
            "pre-condition — the defId-keyed quest is active");
        assertTrue(town.findQuestDef(otherDefId).isPresent(),
            "pre-condition — the other quest is active");

        // The handle's exit path is exactly this: town.removeQuest(packet.defId()).
        // The contract is that the engine removes by defId (not questId).
        town.removeQuest(defId);

        assertAll(
            () -> assertFalse(town.findQuestDef(defId).isPresent(),
                "after removeQuest(defId), findQuestDef(defId) is empty —"
                    + " the defId port was the lookup key, the defId port"
                    + " is the removal key, both consistent."),
            () -> assertTrue(town.findQuestDef(otherDefId).isPresent(),
                "the other quest (different defId) is untouched — removeQuest"
                    + " is a per-defId operation, not a town-wide clear.")
        );
    }

    @Test
    @DisplayName("ADR-0029 — the defId port collapses two quests with the same defId to the second spawn")
    void defIdPortCollapsesSameDefId() {
        // The engine primary key is defId (not questId). A re-spawn with the
        // same defId but a different per-spawn questId swaps the
        // questDefIndex entry. The wire therefore carries defId alone —
        // a client cannot accidentally contribute the "wrong" per-spawn
        // because the engine never keyed on questId.
        String defId = "test_quest_dedup_port";

        Quest first = new Quest();
        first.questId = "instance_first";
        first.defId = defId;

        Quest second = new Quest();
        second.questId = "instance_second";
        second.defId = defId;

        Town town = new Town();
        town.addQuest(first);
        town.addQuest(second);

        Quest visible = town.findQuestDef(defId).orElseThrow();
        assertAll(
            () -> assertSame(second, visible,
                "findQuestDef returns the second addQuest's instance — the"
                    + " defId port is a swap-on-write, not a list. A"
                    + " regression that turns it into a list (or that"
                    + " re-adds the questId-keyed activeQuestMap) would"
                    + " surface the first instance instead and break this"
                    + " assertion."),
            () -> assertEquals("instance_second", visible.questId,
                "the surviving per-spawn questId is the second's — the"
                    + " engine dropped the first's per-spawn identity"
                    + " because the wire carries defId only")
        );
    }
}
