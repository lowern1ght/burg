package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM behaviour pin for {@link Town#addRoadSegment(RoadSegment)} —
 * the road planner's writer into {@link Town#getPlannedRoads()}.
 *
 * <p>This is the {@code :common:test} leg of the planner-population
 * seam. The complementary legs:
 * <ul>
 *   <li>{@link TownStructuralFieldsTest} — the static shape:
 *       {@code plannedRoads} is {@code private final List},
 *       {@link Town#addRoadSegment(RoadSegment)} is
 *       {@code public void (RoadSegment)}. (also {@code :common:test};
 *       reflection-only — no {@code new Town()}.)</li>
 *   <li><b>This file.</b> The behaviour on a real {@link Town}
 *       instance: the append-only contract, the null-drop at the edge,
 *       the insertion-order guarantee, the {@code road_laid} flip on
 *       the first non-null append, and the equal-segments-duplicate
 *       contract the planner relies on.</li>
 *   <li>{@code TownStructuralFlagsRealDerivationsTest} — the MC-aware
 *       leg in :neoforge:test that drives the same mutations through
 *       a real {@link Town} on the merged-JAR classpath. Where this
 *       file reaches {@link Town} via the ADR-0026 carve-out, that
 *       file reaches {@link Town} via the {@code writeServerLegacyClasspath}
 *       classpath injection.</li>
 * </ul>
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>Append-on-commit.</b> {@link Town#addRoadSegment(RoadSegment)}
 *       appends the segment to {@code plannedRoads} in emission order;
 *       the planner's commit order is the read path's order, no
 *       defensive sort. {@link Town#getPlannedRoads()} exposes the
 *       append directly — index 0 is the first segment committed.</li>
 *   <li><b>Null drop at the edge.</b> {@code addRoadSegment(null)} is
 *       a no-op — the segment is silently dropped, the list stays
 *       on its previous size. The structural flag-set's job is to
 *       record "has a road segment been committed for this town",
 *       and a null segment is never a meaningful answer.</li>
 *   <li><b>{@code road_laid} flips on the first non-null append.</b>
 *       {@code Town.structuralFlags().roadLaid()} returns {@code true}
 *       the moment a non-null segment lands, regardless of type or
 *       waypoints. {@code Town.structuralFlags().industryZoned()}
 *       stays {@code false} — the two legs are independent writers,
 *       they do not cross-pollute.</li>
 *   <li><b>Equal segments DO duplicate.</b> Two {@code addRoadSegment}
 *       calls with structurally-equal segments (same start, end,
 *       waypoints, type) produce a two-element list — the mutator
 *       is append-only, not a {@code Set}. The planner commits one
 *       segment per produced plan step; deduplicating equal segments
 *       would silently drop the planner's emit count.</li>
 *   <li><b>Referential identity preserved.</b> {@link Town#getPlannedRoads()}
 *       hands back the same {@link RoadSegment} instances the planner
 *       passed in — the mutator does not defensively copy. This is the
 *       natural pairing of "the planner owns its output shape" — the
 *       planner's downstream consumers compare against the original
 *       segments, and equality is value-based on the record fields
 *       anyway (defensive copy would be wasted allocation).</li>
 * </ol>
 */
class TownAddRoadSegmentFromPlannerTest {

    @Test
    @DisplayName("addRoadSegment appends one segment on a fresh town — getPlannedRoads size is 1, the segment is the same instance")
    void addRoadSegmentAppendsSingleSegment() {
        Town town = new Town();
        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        RoadSegment seg = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);

        town.addRoadSegment(seg);

        List<RoadSegment> observed = town.getPlannedRoads();
        assertAll(
            () -> assertNotNull(observed,
                "getPlannedRoads returns a non-null view — the road planner's read surface"),
            () -> assertEquals(1, observed.size(),
                "one append lands exactly one element — the list is append-only, not dedup'd"),
            () -> assertSame(seg, observed.get(0),
                "the planner's segment is handed back by reference — the mutator does not"
                    + " defensively copy; identity is preserved end-to-end so a downstream"
                    + " consumer can compare against the original segment instance"),
            () -> assertTrue(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() flips true on the first non-null append — the"
                    + " structural triple's permissive leg fires regardless of segment type or length"),
            () -> assertEquals(StructuralFlags.of(false, false, true), town.structuralFlags(),
                "structuralFlags() returns the road-only partial — road_laid=true alone,"
                    + " corePopulated real-derivation false (no buildings), industryZoned"
                    + " false (no zoning-layer calls); non-NONE shapes compare by record equality")
        );
    }

    @Test
    @DisplayName("addRoadSegment(null) is dropped at the edge — list size and road_laid stay on the previous floor")
    void addRoadSegmentNullDroppedAtEdge() {
        Town town = new Town();
        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        RoadSegment seg = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);

        // The null lands first — the floor must hold.
        town.addRoadSegment(null);

        assertAll(
            () -> assertEquals(0, town.getPlannedRoads().size(),
                "null at the edge is silently dropped — the list stays empty, the no-progress"
                    + " floor holds; a regression that allowed null to bump the count would"
                    + " flip road_laid to true on a no-op commit"),
            () -> assertEquals(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays at NONE after a dropped null — the road_laid leg does"
                    + " not fire on a no-op append, the strict-derivation floor holds")
        );

        // A real segment lands afterwards — the floor flips on the non-null append.
        town.addRoadSegment(seg);

        assertAll(
            () -> assertEquals(1, town.getPlannedRoads().size(),
                "a subsequent non-null append lands exactly one element — the dropped null"
                    + " did not advance the index"),
            () -> assertSame(seg, town.getPlannedRoads().get(0),
                "the planner's segment is the one appended after the dropped null —"
                    + " insertion order preserved"),
            () -> assertTrue(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() flips true on the first non-null append after"
                    + " a series of dropped nulls — the floor only fires on real commits")
        );
    }

    @Test
    @DisplayName("addRoadSegment preserves emission order — segments read back in the order the planner committed them")
    void addRoadSegmentPreservesEmissionOrder() {
        Town town = new Town();
        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        BlockPos c = new BlockPos(8, 70, 0);
        BlockPos d = new BlockPos(12, 70, 0);
        RoadSegment first  = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);
        RoadSegment second = new RoadSegment(b, c, List.of(b, c), RoadType.BRIDGE);
        RoadSegment third  = new RoadSegment(c, d, List.of(c, d), RoadType.STREET);

        town.addRoadSegment(first);
        town.addRoadSegment(second);
        town.addRoadSegment(third);

        List<RoadSegment> observed = town.getPlannedRoads();
        assertAll(
            () -> assertEquals(3, observed.size(),
                "three appends land three elements — the list is append-only"),
            () -> assertSame(first, observed.get(0),
                "insertion order preserved: first segment is index 0 — the planner's commit"
                    + " order is the read path's order, no defensive sort"),
            () -> assertSame(second, observed.get(1),
                "insertion order preserved: second segment is index 1 — appended after the"
                    + " first in the order the planner committed"),
            () -> assertSame(third, observed.get(2),
                "insertion order preserved: third segment is index 2 — appended last in"
                    + " the order the planner committed"),
            () -> assertTrue(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() stays true after multiple appends — the"
                    + " permissive leg is non-revertible through addRoadSegment alone")
        );
    }

    @Test
    @DisplayName("equal-segments duplicate — addRoadSegment is append-only, not a Set; the planner's emit count is preserved")
    void equalSegmentsDuplicate() {
        Town town = new Town();
        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        RoadSegment first  = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);
        // A structurally-equal segment — same start, end, waypoints, type.
        // Constructed as a separate instance so we can assertSame on the
        // two reads and confirm the mutator does not collapse them.
        RoadSegment second = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);

        town.addRoadSegment(first);
        town.addRoadSegment(second);

        List<RoadSegment> observed = town.getPlannedRoads();
        assertAll(
            () -> assertEquals(2, observed.size(),
                "two appends of structurally-equal segments land two entries — the mutator"
                    + " is append-only, not a Set; deduplication would silently drop the"
                    + " planner's emit count and corrupt the road graph the engine reads"),
            () -> assertSame(first, observed.get(0),
                "first append is at index 0 — the original segment instance, by reference"),
            () -> assertSame(second, observed.get(1),
                "second append is at index 1 — the structurally-equal segment is a"
                    + " different instance and lives alongside the first; record value-equality"
                    + " does not collapse to a single entry"),
            () -> assertTrue(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() stays true — one or many appends, the"
                    + " permissive leg fires the same way")
        );
    }
}