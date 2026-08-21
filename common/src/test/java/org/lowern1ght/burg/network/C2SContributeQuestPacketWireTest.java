package org.lowern1ght.burg.network;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-0029 wire-shape pin for {@link C2SContributeQuestPacket}. The
 * rename collapsed the C2S payload onto {@code defId} only — the engine
 * primary key — and dropped the per-spawn {@code questId} field. This
 * test pins the discipline at the JVM reflection layer so a future carve
 * that re-adds {@code questId} (because someone mistakes it for client-
 * side identity) fails the build at the {@code :common:test} target
 * before it reaches the wire.
 *
 * <p><b>Why reflection, not behavioural codecs.</b> The
 * {@code C2SContributeQuestPacket.STREAM_CODEC} body is a
 * {@link net.minecraft.network.StreamCodec}, which only resolves at MC
 * class-launch — that's a {@code :neoforge:test} surface. The record's
 * component list is plain JVM reflection (the merged JAR on the
 * {@code :common:test} classpath is enough to load {@link BlockPos} as a
 * type), and the codec shape is the same shape the byte buffer enforces
 * — losing one component means losing one field on the wire. This is the
 * cheapest place to assert "no {@code questId} field exists on the
 * packet".
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>Record components are exactly {@code anchorPos, defId}.</b>
 *       {@link C2SContributeQuestPacket} is a {@code public record}; its
 *       declared components are the wire payload. ADR-0029 collapsed the
 *       payload onto {@code defId} — the engine primary key. A future
 *       carve that re-adds a {@code questId} component (or any other
 *       component) fails this assertion.</li>
 *   <li><b>Component types are {@code BlockPos, String}.</b> The
 *       component type is what the {@code STREAM_CODEC} writes and reads
 *       — a regression that flips the type of either component would
 *       change the on-wire byte layout.</li>
 *   <li><b>{@code questId} is not a record component.</b> The per-spawn
 *       UUID-8-char is client-side identity (carried by the S2C hub data
 *       the client received earlier) and does not traverse the C2S wire.
 *       Re-introducing it would duplicate an id the client already has
 *       and couple the wire to a per-spawn value the engine tick mints
 *       freshly on each spawn.</li>
 * </ol>
 *
 * <p>What this does <i>not</i> pin (and why a future carve does not need
 * to redo it):
 * <ul>
 *   <li>The byte-level codec shape (encode → decode round-trip equality,
 *       exact readable byte count after encode) lives in
 *       {@code :neoforge:test}'s {@code C2SContributeQuestPacketHandleTest}
 *       — the {@code FriendlyByteBuf} construction needs the merged JAR's
 *       Netty buffer support, which is on the legacy classpath the
 *       {@code :neoforge:test} target injects.</li>
 *   <li>The {@link Town#findQuestDef(String)} engine port that the
 *       packet's handle resolves via is pinned in
 *       {@code :common:test}'s {@code TownQuestLogSotTest} (signature)
 *       and the {@code :neoforge:test} {@code TickSchedulerQuestTickPortTest}
 *       (end-to-end through the tick).</li>
 *   <li>The absence of a {@code questId()} accessor method on the
 *       packet. The record-component check above is the source of truth
 *       for wire shape; {@code STREAM_CODEC.encode} walks record
 *       components, not methods. A hand-rolled {@code public String
 *       questId()} method that isn't a record component would compile
 *       here but would never reach the wire — and would be linted by
 *       the {@code :neoforge:test} codec round-trip if anyone tried to
 *       use it as a packet field.</li>
 * </ul>
 */
class C2SContributeQuestPacketWireTest {

    @Test
    @DisplayName("ADR-0029 — C2SContributeQuestPacket record components are exactly (anchorPos: BlockPos, defId: String)")
    void recordComponentsAreExactlyAnchorAndDef() {
        RecordComponent[] components = C2SContributeQuestPacket.class.getRecordComponents();

        assertNotNull(components,
            "C2SContributeQuestPacket is a record and must expose its declared components");
        List<String> names = Stream.of(components).map(RecordComponent::getName).toList();

        assertAll(
            () -> assertEquals(List.of("anchorPos", "defId"), names,
                "the wire payload is anchorPos + defId only — ADR-0029 collapsed"
                    + " the engine lookup onto the defId port and dropped the"
                    + " per-spawn questId from the C2S payload. A future carve"
                    + " that re-adds questId (or any other component) breaks this"
                    + " assertion before the change reaches the wire."),
            () -> assertTrue(names.contains("anchorPos"),
                "anchorPos is the first component — the BlockPos the player clicked"),
            () -> assertTrue(names.contains("defId"),
                "defId is the second component — the quest definition id the"
                    + " engine resolves via Town.findQuestDef"),
            () -> assertTrue(names.contains("questId") == false,
                "questId is NOT a record component — the per-spawn UUID-8-char"
                    + " is client-side identity (carried by the S2C hub data the"
                    + " client received earlier) and does not traverse the C2S"
                    + " wire. Re-introducing it would duplicate an id the client"
                    + " already has, and would couple the wire to a per-spawn"
                    + " value that the engine tick mints freshly each spawn."),
            () -> assertEquals(BlockPos.class, components[0].getType(),
                "the first component is a BlockPos — anchorPos. Flipping this"
                    + " would change the on-wire byte layout."),
            () -> assertEquals(String.class, components[1].getType(),
                "the second component is a String — defId, the UTF-8 wire field")
        );
    }
}
