package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadSegment;
import org.lowern1ght.burg.behavior.road.RoadType;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MC-aware behavior tests for the structural-flags derivations. The
 * {@link Town#industryZoned()} and {@link Town#roadLaid()} legs of
 * {@link Town#structuralFlags()} read the per-town SoTs the
 * {@code :common:test} {@link TownStructuralFieldsTest} pins —
 * {@code zoningCount} and {@code plannedRoads}. The signature pin
 * keeps the {@code :common:test} discipline (no {@code new Town()} on
 * the bare-JVM classpath); the behavior assertions that need an
 * instance land here, where the ModDev merged JAR is on the
 * classpath.
 *
 * <p><b>What this pins.</b> Three claims about the structural-flags
 * derivations that the act-5 mutator seam makes true:
 *
 * <ol>
 *   <li><b>Fresh-town collapse.</b> A {@code new Town()} with no
 *       zoning or road-planner calls reports {@code industryZoned}
 *       and {@code roadLaid} both {@code false}, and
 *       {@code structuralFlags()} collapses to
 *       {@link StructuralFlags#NONE} regardless of acquisition —
 *       exactly the no-progress floor the act-4 follow-up landed.</li>
 *   <li><b>Zoning leg flips on the first increment.</b>
 *       {@link Town#addZoning(Town.Zone, int)} populates the map;
 *       {@code industryZoned()} flips to {@code true} on the next
 *       {@code structuralFlags()} read. Multiple calls for the same
 *       zone aggregate via the merge, so a layer that emits
 *       decisions incrementally accumulates naturally. Negative
 *       counts and null zones are dropped at the edge — the
 *       structural flag-set's job is to record "has the zoning
 *       layer touched this town", and a negative count is never a
 *       meaningful answer to that question.</li>
 *   <li><b>Road leg flips on the first append.</b>
 *       {@link Town#addRoadSegment(RoadSegment)} appends to the list;
 *       {@code roadLaid()} flips to {@code true} on the next read.
 *       Null segments are dropped at the edge. Insertion order
 *       preserved so the read path sees the segments the planner
 *       committed in the order it committed them.</li>
 * </ol>
 *
 * <p>The hub-mode predicate that consumes these flags is pinned
 * separately by {@code TownFacadeTest} — this file pins the
 * derivations' behavior, not the predicate's three-leg composition.
 */
class TownStructuralFlagsRealDerivationsTest {

    @Test
    @DisplayName("fresh town — industryZoned and roadLaid both false; structuralFlags collapses to NONE")
    void freshTownDerivesNone() {
        Town town = new Town();

        assertAll(
            () -> assertEquals(Map.of(), town.getZoningCount(),
                "fresh town's zoningCount is empty — no zoning-layer calls landed"),
            () -> assertEquals(List.of(), town.getPlannedRoads(),
                "fresh town's plannedRoads is empty — no road-planner commits landed"),
            () -> assertFalse(invokeIndustryZoned(town),
                "industryZoned() is false on a fresh town — empty zoningCount, the strict floor"),
            () -> assertFalse(invokeRoadLaid(town),
                "roadLaid() is false on a fresh town — empty plannedRoads, the strict floor"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() collapses to NONE on a fresh town — all three legs false,"
                    + " the gate's no-progress floor")
        );
    }

    @Test
    @DisplayName("addZoning(INDUSTRY, 8) — industryZoned flips true and the count is observed via getZoningCount")
    void addZoningFlipsIndustryZoned() {
        Town town = new Town();

        town.addZoning(Town.Zone.INDUSTRY, 8);

        assertAll(
            () -> assertEquals(Map.of(Town.Zone.INDUSTRY, 8), town.getZoningCount(),
                "getZoningCount observes the increment, Map.of-form for a single-zone write"),
            () -> assertTrue(invokeIndustryZoned(town),
                "industryZoned() is true once any zone entry lands — the structural triple's"
                    + " permissive leg flips regardless of which zone the layer chose"),
            () -> assertFalse(invokeRoadLaid(town),
                "roadLaid() is still false — the road-planner commit is independent of the"
                    + " zoning layer; the two legs don't cross-pollute"),
            () -> assertEquals(StructuralFlags.of(false, true, false), town.structuralFlags(),
                "structuralFlags() returns the zoning-only partial — industryZoned=true alone,"
                    + " corePopulated real-derivation false (no buildings), roadLaid false;"
                    + " non-NONE shapes compare by record equality, not referential identity")
        );
    }

    @Test
    @DisplayName("addZoning multiple calls for the same zone — merge sums; different zones coexist")
    void addZoningMergesAcrossCalls() {
        Town town = new Town();

        town.addZoning(Town.Zone.INDUSTRY, 4);
        town.addZoning(Town.Zone.INDUSTRY, 6);
        town.addZoning(Town.Zone.MILITARY, 3);
        town.addZoning(Town.Zone.CORE, 12);

        // Java Map.of has at most 10 K-V pairs and an EnumMap allows null values; we have 3 keys
        // with positive ints, all safe.
        Map<Town.Zone, Integer> observed = town.getZoningCount();
        assertAll(
            () -> assertEquals(3, observed.size(),
                "three distinct zones tracked — INDUSTRY merged, MILITARY + CORE singletons"),
            () -> assertEquals(10, observed.get(Town.Zone.INDUSTRY),
                "INDUSTRY entries 4 + 6 merge to 10 via Integer::sum — the merge function the"
                    + " mutator uses is the canonical Map.merge reducer"),
            () -> assertEquals(3, observed.get(Town.Zone.MILITARY),
                "MILITARY is the singleton entry the layer added — the count is observed verbatim"),
            () -> assertEquals(12, observed.get(Town.Zone.CORE),
                "CORE is the singleton entry the layer added — the count is observed verbatim"),
            () -> assertTrue(invokeIndustryZoned(town),
                "industryZoned() stays true — multiple zones do not un-flip the derivation")
        );
    }

    @Test
    @DisplayName("addZoning drops null zone + non-positive cells at the edge — structural flag-set records 'touched', not 'cleared'")
    void addZoningDropsEdgeCases() {
        Town town = new Town();

        town.addZoning(null, 5);
        town.addZoning(Town.Zone.INDUSTRY, 0);
        town.addZoning(Town.Zone.INDUSTRY, -1);

        assertAll(
            () -> assertEquals(Map.of(), town.getZoningCount(),
                "null zone, zero cells, and negative cells are all dropped silently — the map"
                    + " stays empty after three dropped calls, exactly what a 'no progress' zone"
                    + " layer would emit"),
            () -> assertFalse(invokeIndustryZoned(town),
                "industryZoned() stays false — the no-progress floor holds when every call is"
                    + " dropped at the edge; the gate does not fire on a no-op layer")
        );

        // After a real call, the map is non-empty and industryZoned flips.
        town.addZoning(Town.Zone.INDUSTRY, 1);
        assertTrue(invokeIndustryZoned(town),
            "industryZoned() flips on the first non-dropped call — the merge semantics work"
                + " even after a series of dropped edge inputs");
    }

    @Test
    @DisplayName("addRoadSegment appends in emission order; roadLaid flips true; null is dropped at the edge")
    void addRoadSegmentAccumulates() {
        Town town = new Town();

        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        BlockPos c = new BlockPos(8, 70, 0);
        RoadSegment first  = new RoadSegment(a, b, List.of(a, b), RoadType.STREET);
        RoadSegment second = new RoadSegment(b, c, List.of(b, c), RoadType.BRIDGE);

        town.addRoadSegment(first);
        town.addRoadSegment(null);   // dropped at the edge
        town.addRoadSegment(second);

        List<RoadSegment> observed = town.getPlannedRoads();
        assertAll(
            () -> assertEquals(2, observed.size(),
                "two segments observed — null at the edge does not bump the count"),
            () -> assertSame(first, observed.get(0),
                "insertion order preserved: first segment is index 0 — the planner's commit"
                    + " order is the read path's order, no defensive sort"),
            () -> assertSame(second, observed.get(1),
                "insertion order preserved: second segment is index 1 — appended after the"
                    + " dropped null did not advance the index"),
            () -> assertTrue(invokeRoadLaid(town),
                "roadLaid() flips true on the first non-null append — the structural triple's"
                    + " permissive leg fires regardless of segment type or length")
        );
    }

    @Test
    @DisplayName("structuralFlags reflects the latest state — zoning flips industry_zoned, road flips road_laid")
    void structuralFlagsAggregatesBothLegs() {
        Town town = new Town();

        assertSame(StructuralFlags.NONE, town.structuralFlags(),
            "structuralFlags() starts at NONE on a fresh town");

        town.addZoning(Town.Zone.INDUSTRY, 1);
        assertEquals(StructuralFlags.of(false, true, false), town.structuralFlags(),
            "structuralFlags() reflects the first zoning increment — industry_zoned flips,"
                + " the other two legs still on the floor");

        BlockPos a = new BlockPos(0, 70, 0);
        BlockPos b = new BlockPos(4, 70, 0);
        town.addRoadSegment(new RoadSegment(a, b, List.of(a, b), RoadType.STREET));
        assertEquals(StructuralFlags.of(false, true, true), town.structuralFlags(),
            "structuralFlags() reflects the first road append — road_laid flips, both"
                + " industry_zoned and road_laid are true, the gate's structural leg fires");
    }

    // ------------------------------------------------------------------------
    // Reflection seam — industryZoned() and roadLaid() are package-private on
    // Town (the project's house style: derivation reads stay in the package,
    // outside callers go through structuralFlags()). The :neoforge:test target
    // sits in a different package (org.lowern1ght.burg.town for TownFacadeTest
    // too — wait, that's the same package, so direct calls would work). Use
    // direct calls where the package lines up; use reflection only where it
    // doesn't.
    //
    // TownStructuralFlagsRealDerivationsTest is in org.lowern1ght.burg.town,
    // same as Town.industryZoned() / Town.roadLaid() — direct calls work.
    // ------------------------------------------------------------------------

    private static boolean invokeIndustryZoned(Town town) {
        return town.industryZoned();
    }

    private static boolean invokeRoadLaid(Town town) {
        return town.roadLaid();
    }

    // Sanity check: structuralFlags is the public surface, derivations are
    // package-private — pin the accessor exists for outside callers.
    @Test
    @DisplayName("Town.structuralFlags() is the public read surface — derivations are reachable via structuralFlags()")
    void structuralFlagsIsThePublicSurface() {
        assertNotNull(new Town().structuralFlags(),
            "structuralFlags() is public so outside callers (hubMode, the S2C packet, the"
                + " anchor-right-click log) read the flag-set without going through the"
                + " package-private derivation methods");
    }
}