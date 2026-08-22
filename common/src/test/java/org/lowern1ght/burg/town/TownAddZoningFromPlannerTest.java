package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.StructuralFlags;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM behaviour pin for {@link Town#addZoning(Town.Zone, int)} —
 * the zoning layer's writer into {@link Town#getZoningCount()}.
 *
 * <p>This is the {@code :common:test} leg of the planner-population
 * seam. The complementary legs:
 * <ul>
 *   <li>{@link TownStructuralFieldsTest} — the static shape:
 *       {@code zoningCount} is {@code private final Map},
 *       {@link Town#addZoning(Town.Zone, int)} is
 *       {@code public void (Zone, int)}. (also {@code :common:test};
 *       reflection-only — no {@code new Town()}.)</li>
 *   <li><b>This file.</b> The behaviour on a real {@link Town}
 *       instance: the merge-aggregate contract, the null/non-positive
 *       edge drops, the {@code industryZoned} permissive-leg flip on
 *       the first non-dropped call, and the {@link Town.Zone}
 *       taxonomy's role as the structural triple's permissive
 *       leg trigger.</li>
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
 *   <li><b>Merge-aggregate on the same zone.</b>
 *       {@link Town#addZoning(Town.Zone, int)} merges per-zone via
 *       {@link Map#merge} with {@code Integer::sum}. Two calls for the
 *       same zone accumulate; the {@link Town#getZoningCount()} view
 *       hands back a {@link Map} that shows the merged total. The
 *       zoning layer emits decisions incrementally — a single
 *       decision can span multiple calls; the merge is the natural
 *       reducer for "many emits → one count".</li>
 *   <li><b>Distinct zones coexist.</b> {@code addZoning(ZONE_A, n)} +
 *       {@code addZoning(ZONE_B, m)} land two entries; the map's
 *       per-key shape matches {@link Town.Zone} ordinal-keyed
 *       {@link java.util.EnumMap} reads. Each zone's count is
 *       independent — adding to {@code CORE} does not bump
 *       {@code INDUSTRY}.</li>
 *   <li><b>{@code industryZoned} flips on the first non-dropped
 *       increment, regardless of zone.</b> {@code industryZoned()}
 *       reads {@code !zoningCount.isEmpty()} — the structural
 *       triple's permissive leg fires on the first entry, irrespective
 *       of which {@link Town.Zone} the layer chose. CORE counts
 *       because the leg is about "has the zoning layer touched this
 *       town", not "is the INDUSTRY zone filled".</li>
 *   <li><b>Null zone + non-positive cells dropped at the edge.</b>
 *       {@code addZoning(null, n)} is a no-op — null zone is dropped
 *       silently. {@code addZoning(zone, 0)} and
 *       {@code addZoning(zone, -k)} are also no-ops — the cell count
 *       is non-positive. The strict-derivation floor holds when every
 *       call is dropped at the edge.</li>
 *   <li><b>{@code corePopulated} stays on the real-derivation floor.</b>
 *       {@link Town#addZoning(Town.Zone, int)} writes the zoning
 *       count map, not the building roll. {@code corePopulated()}
 *       reads the placed buildings and their bounding boxes — without
 *       any placed building, the real-derivation returns {@code false}
 *       regardless of how many zoning-layer calls landed. The zoning
 *       and core legs are independent writers.</li>
 *   <li><b>{@code road_laid} stays on the floor — no cross-pollution.</b>
 *       {@link Town#addZoning(Town.Zone, int)} does not call
 *       {@link Town#addRoadSegment} or anything else that would
 *       flip the road leg. The two mutators are independent writers
 *       of two independent SoTs.</li>
 * </ol>
 */
class TownAddZoningFromPlannerTest {

    @Test
    @DisplayName("addZoning(CORE, 5) on a fresh town — getZoningCount observes the singleton CORE entry; industryZoned flips true")
    void addZoningFlipsIndustryZoned() {
        Town town = new Town();

        // CORE explicitly — the user-prompt spec named CORE as the test zone.
        // The mutation lands in the per-zone EnumMap; industryZoned()
        // reads map emptiness, not the INDUSTRY-specific entry.
        town.addZoning(Town.Zone.CORE, 5);

        assertAll(
            () -> assertEquals(Map.of(Town.Zone.CORE, 5), town.getZoningCount(),
                "addZoning(Zone.CORE, 5) lands the singleton CORE entry — the merge"
                    + " semantics see exactly one zone with exactly 5 cells; the"
                    + " EnumMap's view through getZoningCount matches the wire shape"),
            () -> assertTrue(town.structuralFlags().industryZoned(),
                "structuralFlags().industryZoned() flips true on the first non-dropped"
                    + " increment — the structural triple's permissive leg fires regardless"
                    + " of which zone the layer chose; CORE counts because industryZoned()"
                    + " reads map emptiness, not the INDUSTRY entry"),
            () -> assertFalse(town.structuralFlags().corePopulated(),
                "structuralFlags().corePopulated() stays false — the real-derivation reads"
                    + " placed buildings, not the zoning count map; with no placed"
                    + " buildings the floor holds; addZoning does not cross-pollute core"),
            () -> assertFalse(town.structuralFlags().roadLaid(),
                "structuralFlags().roadLaid() stays false — the zoning-layer mutator does"
                    + " not cross-pollute the road leg; the two legs are independent writers"),
            () -> assertEquals(StructuralFlags.of(false, true, false), town.structuralFlags(),
                "structuralFlags() returns the zoning-only partial — industryZoned=true"
                    + " alone, corePopulated real-derivation false (no buildings), roadLaid"
                    + " false; non-NONE shapes compare by record equality, not referential"
                    + " identity")
        );
    }

    @Test
    @DisplayName("addZoning multiple calls for the same zone — merge sums via Integer::sum; distinct zones coexist")
    void addZoningMergesAcrossCalls() {
        Town town = new Town();

        town.addZoning(Town.Zone.INDUSTRY, 4);
        town.addZoning(Town.Zone.INDUSTRY, 6);
        town.addZoning(Town.Zone.MILITARY, 3);
        town.addZoning(Town.Zone.CORE, 12);

        Map<Town.Zone, Integer> observed = town.getZoningCount();
        assertAll(
            () -> assertEquals(3, observed.size(),
                "three distinct zones tracked — INDUSTRY merged, MILITARY + CORE singletons"),
            () -> assertEquals(10, observed.get(Town.Zone.INDUSTRY),
                "INDUSTRY entries 4 + 6 merge to 10 via Integer::sum — the merge function"
                    + " the mutator uses is the canonical Map.merge reducer; a regression"
                    + " that switched to overwrite would silently drop the planner's emit count"),
            () -> assertEquals(3, observed.get(Town.Zone.MILITARY),
                "MILITARY is the singleton entry the layer added — the count is observed"
                    + " verbatim; adding to a distinct zone never merges with INDUSTRY"),
            () -> assertEquals(12, observed.get(Town.Zone.CORE),
                "CORE is the singleton entry the layer added — the count is observed"
                    + " verbatim; the per-zone independence holds end-to-end"),
            () -> assertTrue(town.structuralFlags().industryZoned(),
                "structuralFlags().industryZoned() stays true — multiple zones do not"
                    + " un-flip the derivation; the permissive leg fires on the first"
                    + " non-dropped increment and stays fired")
        );
    }

    @Test
    @DisplayName("addZoning drops null zone + non-positive cells at the edge — the strict-derivation floor holds on a no-op layer")
    void addZoningDropsEdgeCases() {
        Town town = new Town();

        town.addZoning(null, 5);
        town.addZoning(Town.Zone.INDUSTRY, 0);
        town.addZoning(Town.Zone.INDUSTRY, -1);

        assertAll(
            () -> assertEquals(Map.of(), town.getZoningCount(),
                "null zone, zero cells, and negative cells are all dropped silently — the"
                    + " map stays empty after three dropped calls, exactly what a no-progress"
                    + " zoning layer would emit; a regression that allowed null zone to"
                    + " bump the map would silently re-enable the layer on a null payload"),
            () -> assertSame(StructuralFlags.NONE, town.structuralFlags(),
                "structuralFlags() stays at NONE — the no-progress floor holds when every"
                    + " call is dropped at the edge; the gate does not fire on a no-op layer")
        );

        // After a real call, the map is non-empty and the permissive leg flips.
        town.addZoning(Town.Zone.INDUSTRY, 1);

        assertAll(
            () -> assertEquals(Map.of(Town.Zone.INDUSTRY, 1), town.getZoningCount(),
                "the post-drop call lands the singleton INDUSTRY entry — the merge"
                    + " semantics work even after a series of dropped edge inputs"),
            () -> assertTrue(town.structuralFlags().industryZoned(),
                "structuralFlags().industryZoned() flips on the first non-dropped call after"
                    + " a series of dropped edge inputs — the permissive leg is not"
                    + " sticky-false on dropped calls; only on no-progress saves")
        );
    }

    @Test
    @DisplayName("addZoning(Zone, int) is the only sanctioned writer — setZoningCount-style re-binding would break the read site")
    void zoningCountFieldIsOnlySetViaAddZoning() throws Exception {
        // A regression that re-introduced a `setZoningCount(Map)` setter would
        // give external code a back-door that bypasses the edge drops. Pin the
        // absence: there is no setter on Town that takes a Map<Zone, Integer>.
        boolean hasSetter = false;
        String foundName = "";
        for (var m : Town.class.getMethods()) {
            if (m.getName().startsWith("set") && m.getParameterCount() == 1) {
                Class<?> t = m.getParameterTypes()[0];
                if (t.equals(Map.class)) {
                    hasSetter = true;
                    foundName = m.getName();
                    break;
                }
            }
        }
        assertFalse(hasSetter,
            "Town has no public setter that takes a Map — the structural SoT is mutated"
                + " only via addZoning(Zone, int); a regression that re-introduced a"
                + " setZoningCount(Map) back-door would silently bypass the edge drops"
                + " (null zone / non-positive cells) the mutator enforces. (Found:"
                + " " + foundName + ")");
    }
}