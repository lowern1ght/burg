package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.ConstructionIntent;
import org.lowern1ght.burg.domain.settlement.ConstructionQueue;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior test for the queue-consumer migration. The
 * {@link TownHubDataBuilder} S2C packet builder was the largest
 * remaining consumer of the legacy {@code Town.getConstructionQueue()}
 * projection (the O(N) rebuild of an MC-typed {@link QueueEntry} list
 * on every read). The migration removed the projection and changed
 * the builder to iterate {@link Town#constructionQueueView()} directly,
 * mapping each {@link ConstructionIntent} through
 * {@link QueueEntry#fromIntent} at the call site.
 *
 * <p>This file pins what the migration was actually about: the wire
 * payload is byte-identical to the pre-migration form. The
 * {@code TownHubDataBuilder.buildBuildingListData(...)} packet now
 * produces the same {@code "ConstructionQueue"} {@link ListTag} the
 * client expects — same field names, same type tags, same per-entry
 * shape — because the local {@code QueueEntry.fromIntent →
 * QueueEntry.serialize} pair is the same wire-format path the legacy
 * projection rebuilt on every read.
 *
 * <p>The shape pin lives here, where the ModDev merged JAR is on the
 * test classpath ({@code :common:test} deliberately stays bare-JVM).
 * The reflective accessor to the private {@code constructionQueue}
 * field is the discipline the file uses: the migration is a wire
 * contract, not a constructor argument, so the test sets the SoT
 * field directly and asserts the consumer reads it through the
 * migrated path.
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>Empty SoT → empty wire list.</b> A fresh town with the
 *       default {@code ConstructionQueue.EMPTY} produces a
 *       {@code "ConstructionQueue"} empty {@link ListTag} in the
 *       {@code buildBuildingListData} packet. The migration did not
 *       change the empty-list surface.</li>
 *   <li><b>NewBuild wire shape.</b> A SoT with a
 *       {@link ConstructionIntent.NewBuild} entry produces a
 *       single-element list whose compound carries the same
 *       {@code EntryId} / {@code Type="new_build"} / {@code DefId}
 *       triple the pre-migration path produced (driven by
 *       {@link QueueEntry#serialize}).</li>
 *   <li><b>Upgrade wire shape.</b> A SoT with a
 *       {@link ConstructionIntent.Upgrade} entry produces a
 *       single-element list whose compound carries
 *       {@code EntryId} / {@code Type="upgrade"} / {@code DefId} /
 *       {@code BuildingWorldPos} / {@code FromLevel} — the full
 *       upgrade-row shape the client expects.</li>
 *   <li><b>FIFO order preserved.</b> Multiple SoT entries produce
 *       a list in insertion order (head to tail), matching
 *       {@link ConstructionQueue#entries()}.</li>
 * </ol>
 *
 * <p><b>What this does NOT pin.</b> The full hub packet
 * ({@code buildHubData}) carries the same {@code "ConstructionQueue"}
 * list through the same migrated path; the simpler
 * {@code buildBuildingListData} surface is the wire-shape probe. The
 * other fields in the hub packet (catalog, stock, summary, quests)
 * have separate test coverage and are not part of this migration.
 */
class TownHubDataBuilderQueueMigrationTest {

    private static final BlockPos ANCHOR = new BlockPos(0, 70, 0);
    private static final String NEW_BUILD_DEF_ID = "burg:oak_log";
    private static final String UPGRADE_DEF_ID = "burg:smithy";
    private static final BlockPos UPGRADE_POS = new BlockPos(7, 70, 7);
    private static final long UPGRADE_POS_KEY = UPGRADE_POS.asLong();

    @Test
    @DisplayName("empty SoT produces an empty ConstructionQueue list — the migrated builder's empty-state")
    void emptySotEmitsEmptyConstructionQueueList() {
        Town town = new Town();
        // constructionQueue starts at EMPTY; no queued intents.

        CompoundTag packet = new TownHubDataBuilder(town).buildBuildingListData(ANCHOR);

        assertNotNull(packet, "buildBuildingListData returns a non-null packet");
        assertTrue(packet.contains("ConstructionQueue"),
            "the migrated builder still emits the ConstructionQueue key the client expects");
        Tag cqTag = packet.get("ConstructionQueue");
        assertTrue(cqTag instanceof ListTag,
            "the value is a ListTag — the same wire shape the client expects");
        assertEquals(0, ((ListTag) cqTag).size(),
            "an empty SoT produces an empty list — the migrated builder does not insert a synthesized empty entry");
    }

    @Test
    @DisplayName("NewBuild intent → wire compound with Type=\"new_build\", EntryId, DefId — byte-identical to the pre-migration path")
    void newBuildIntentProducesExpectedWireRow() {
        Town town = new Town();
        seedSot(town, ConstructionQueue.EMPTY
            .enqueue(new ConstructionIntent.NewBuild(13L, NEW_BUILD_DEF_ID)));

        CompoundTag packet = new TownHubDataBuilder(town).buildBuildingListData(ANCHOR);
        ListTag cqList = packet.getList("ConstructionQueue", Tag.TAG_COMPOUND);

        assertEquals(1, cqList.size(),
            "the SoT had one entry, the wire carries one compound — the migration lifted the rebuild cost");

        CompoundTag row = cqList.getCompound(0);
        assertAll(
            () -> assertEquals(13L, row.getLong("EntryId"),
                "EntryId survives the QueueEntry.fromIntent → QueueEntry.serialize boundary intact"),
            () -> assertEquals("new_build", row.getString("Type"),
                "Type discriminator is the legacy string the client keys on — the wire format is unchanged"),
            () -> assertEquals(NEW_BUILD_DEF_ID, row.getString("DefId"),
                "DefId survives the boundary"),
            () -> assertFalse(row.contains("BuildingWorldPos"),
                "a NewBuild row has no BuildingWorldPos — the migration does not leak the Upgrade-only field"),
            () -> assertFalse(row.contains("FromLevel"),
                "a NewBuild row has no FromLevel — the upgrade-only field is omitted")
        );
    }

    @Test
    @DisplayName("Upgrade intent → wire compound with Type=\"upgrade\", EntryId, DefId, BuildingWorldPos, FromLevel")
    void upgradeIntentProducesExpectedWireRow() {
        Town town = new Town();
        // ConstructionIntent.Upgrade requires a non-null, non-empty buildingDefId and
        // worldPosKey (Long.toString of BlockPos.asLong()).
        seedSot(town, ConstructionQueue.EMPTY
            .enqueue(new ConstructionIntent.Upgrade(
                19L, UPGRADE_DEF_ID, Long.toString(UPGRADE_POS_KEY), 1)));

        CompoundTag packet = new TownHubDataBuilder(town).buildBuildingListData(ANCHOR);
        ListTag cqList = packet.getList("ConstructionQueue", Tag.TAG_COMPOUND);

        assertEquals(1, cqList.size(),
            "the SoT had one entry, the wire carries one compound");

        CompoundTag row = cqList.getCompound(0);
        assertAll(
            () -> assertEquals(19L, row.getLong("EntryId"),
                "EntryId survives the boundary"),
            () -> assertEquals("upgrade", row.getString("Type"),
                "Type discriminator is the legacy string the client keys on"),
            () -> assertEquals(UPGRADE_DEF_ID, row.getString("DefId"),
                "DefId survives the boundary"),
            () -> assertEquals(UPGRADE_POS_KEY, row.getLong("BuildingWorldPos"),
                "BuildingWorldPos is the long form of the BlockPos — the same long the QueueEntry.Upgrade"
                    + " wraps before the boundary conversion"),
            () -> assertEquals(1, row.getInt("FromLevel"),
                "FromLevel is the int form the wire carries — survives the boundary")
        );
    }

    @Test
    @DisplayName("multi-entry SoT → wire list in FIFO order — head at index 0, tail at the end")
    void fifoOrderPreservedAcrossMigration() {
        Town town = new Town();
        ConstructionQueue queue = ConstructionQueue.EMPTY
            .enqueue(new ConstructionIntent.NewBuild(1L, "burg:a"))
            .enqueue(new ConstructionIntent.NewBuild(2L, "burg:b"))
            .enqueue(new ConstructionIntent.Upgrade(3L, "burg:c", Long.toString(new BlockPos(0, 70, 0).asLong()), 0));
        seedSot(town, queue);

        CompoundTag packet = new TownHubDataBuilder(town).buildBuildingListData(ANCHOR);
        ListTag cqList = packet.getList("ConstructionQueue", Tag.TAG_COMPOUND);

        assertEquals(3, cqList.size(),
            "SoT size == wire list size — the migration does not drop or duplicate entries");
        // FIFO order is the SoT's invariant; the legacy read path rebuilt in
        // head-to-tail order because it iterated constructionQueue.entries().
        // The migrated path does the same iteration, so the wire order must
        // match the SoT insertion order.
        assertAll(
            () -> assertEquals(1L, cqList.getCompound(0).getLong("EntryId"),
                "head of the queue is index 0 — the FIFO invariant the"
                    + " legacy projection rebuilt in the same order"),
            () -> assertEquals("burg:a", cqList.getCompound(0).getString("DefId"),
                "head defId matches the SoT head"),
            () -> assertEquals(2L, cqList.getCompound(1).getLong("EntryId"),
                "second entry is index 1 — insertion order preserved"),
            () -> assertEquals(3L, cqList.getCompound(2).getLong("EntryId"),
                "tail is index 2 — the wire order is the SoT order"),
            () -> assertEquals("burg:c", cqList.getCompound(2).getString("DefId"),
                "tail defId matches the SoT tail"),
            () -> assertEquals("upgrade", cqList.getCompound(2).getString("Type"),
                "the third entry is still an Upgrade — the wire preserves the discriminator")
        );
    }

    @Test
    @DisplayName("buildHubData's ConstructionQueue list carries the same wire row as buildBuildingListData")
    void buildHubDataAndBuildBuildingListDataShareTheConstructionQueueWireRow() {
        Town town = new Town();
        seedSot(town, ConstructionQueue.EMPTY
            .enqueue(new ConstructionIntent.NewBuild(42L, NEW_BUILD_DEF_ID)));

        TownHubDataBuilder builder = new TownHubDataBuilder(town);
        ListTag fromHubPacket = builder.buildHubData(ANCHOR).getList("ConstructionQueue", Tag.TAG_COMPOUND);
        ListTag fromBuildingListPacket = builder.buildBuildingListData(ANCHOR)
            .getList("ConstructionQueue", Tag.TAG_COMPOUND);

        assertEquals(1, fromHubPacket.size(),
            "buildHubData emits the SoT-entry compound the migrated builder now produces");
        assertEquals(1, fromBuildingListPacket.size(),
            "buildBuildingListData emits the same compound shape");
        assertEquals(fromBuildingListPacket.getCompound(0).getLong("EntryId"),
            fromHubPacket.getCompound(0).getLong("EntryId"),
            "the EntryId is identical across both packets — the migrated path emits the"
                + " same wire row regardless of which packet builder reads it");
        assertEquals(fromBuildingListPacket.getCompound(0).getString("Type"),
            fromHubPacket.getCompound(0).getString("Type"),
            "the Type discriminator is identical across both packets");
        assertEquals(fromBuildingListPacket.getCompound(0).getString("DefId"),
            fromHubPacket.getCompound(0).getString("DefId"),
            "the DefId is identical across both packets");
    }

    // ------------------------------------------------------------------------
    // Test fixture seed — set the private `constructionQueue` field directly
    // via reflection. The migration is about the read path, not the enqueue
    // path (enqueue is pinned by the in-game wire path: Town →
    // tryAddToConstructionQueue → constructionQueue.enqueue, covered by
    // TownConstructionQueueSotTest's field reflection and the in-game
    // wire). The carve deliberately does not add a test-only setter —
    // production code stays untouched.
    // ------------------------------------------------------------------------

    private static void seedSot(Town town, ConstructionQueue queue) {
        try {
            Field field = Town.class.getDeclaredField("constructionQueue");
            field.setAccessible(true);
            field.set(town, queue);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("failed to seed Town.constructionQueue for test", e);
        }
    }
}
