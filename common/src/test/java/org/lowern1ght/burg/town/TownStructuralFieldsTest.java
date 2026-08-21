package org.lowern1ght.burg.town;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.behavior.road.RoadSegment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Signature pin for ADR-0026 — the structural-flags fields' mutators.
 *
 * <p>{@link Town#addZoning(Zone, int)} and
 * {@link Town#addRoadSegment(RoadSegment)} are the seams the act-5
 * zoning / road-planner carves wire into the production engine tick.
 * They mutate the per-town SoTs ({@code zoningCount} and
 * {@code plannedRoads}) so {@link Town#structuralFlags()} can flip
 * {@code industry_zoned} and {@code road_laid} from the empty-field
 * floor to {@code true} on the first call.
 *
 * <p>This test pins the surface — the mutators' signatures, the
 * fields they touch, and the type-level guarantees the derivations
 * rely on — without instantiating {@code Town} (which would pull in
 * {@code net.minecraft.*} static init that the {@code :common:test}
 * classpath intentionally does not carry). The behavior cases
 * (mutator invocation, field accumulation, derivation flips) live
 * in the {@code :neoforge:test} target's
 * {@code TownStructuralFlagsRealDerivationsTest}, where the ModDev
 * merged JAR is on the classpath.
 *
 * <p>What this test pins (the discipline that makes the strict
 * {@code structuralFlags()} derivation collapse to
 * {@link org.lowern1ght.burg.domain.settlement.StructuralFlags#NONE}
 * on every fresh save flip the moment the engine calls the mutators):
 * <ol>
 *   <li>{@code Town.addZoning(Zone, int)} is the only sanctioned
 *       write path into {@code zoningCount}; it is {@code public},
 *       {@code void}, and takes a {@link Town.Zone} plus a cell count.</li>
 *   <li>{@code Town.addRoadSegment(RoadSegment)} is the only
 *       sanctioned write path into {@code plannedRoads}; it is
 *       {@code public}, {@code void}, and takes a {@link RoadSegment}.</li>
 *   <li>{@code zoningCount} is the per-town {@link Map}<{@link Town.Zone},
 *       {@link Integer}> SoT — type-erased to {@code Map} at the JVM
 *       level — and {@code plannedRoads} is the per-town
 *       {@link List}<{@link RoadSegment}> SoT — type-erased to
 *       {@code List}. Both stay private.</li>
 *   <li>The derivations {@link Town#industryZoned()} and
 *       {@link Town#roadLaid()} remain package-private; their bodies
 *       read {@code zoningCount.isEmpty()} and
 *       {@code plannedRoads.isEmpty()} respectively — strict form
 *       on a fresh save, true once a mutator lands.</li>
 * </ol>
 */
class TownStructuralFieldsTest {

    @Test
    @DisplayName("Town.addZoning(Zone, int) is the zoningCount mutator — public, void, the two declared parameters")
    void addZoningSignatureIsRight() throws Exception {
        Method mutator = Town.class.getMethod("addZoning", Town.Zone.class, int.class);

        assertNotNull(mutator, "addZoning must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPublic(mutator.getModifiers()),
                "addZoning is public so the zoning layer's tick can reach it from outside Town"),
            () -> assertEquals(void.class, mutator.getReturnType(),
                "addZoning is void — the merge writes into the field; nothing flows back to the caller"),
            () -> assertEquals(2, mutator.getParameterCount(),
                "addZoning takes exactly two parameters (zone, cells)"),
            () -> assertSame(Town.Zone.class, mutator.getParameterTypes()[0],
                "first parameter is Town.Zone — the per-town zone taxonomy the layer increments"),
            () -> assertEquals(int.class, mutator.getParameterTypes()[1],
                "second parameter is int — the cell count the decision covers (merge-friendly)")
        );
    }

    @Test
    @DisplayName("Town.addRoadSegment(RoadSegment) is the plannedRoads mutator — public, void, single parameter")
    void addRoadSegmentSignatureIsRight() throws Exception {
        Method mutator = Town.class.getMethod("addRoadSegment", RoadSegment.class);

        assertNotNull(mutator, "addRoadSegment must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPublic(mutator.getModifiers()),
                "addRoadSegment is public so the road planner's commit path can reach it from outside Town"),
            () -> assertEquals(void.class, mutator.getReturnType(),
                "addRoadSegment is void — the append writes into the field; nothing flows back to the caller"),
            () -> assertEquals(1, mutator.getParameterCount(),
                "addRoadSegment takes exactly one parameter (the segment)"),
            () -> assertSame(RoadSegment.class, mutator.getParameterTypes()[0],
                "the parameter is RoadSegment — the planner's output type, append-only by contract")
        );
    }

    @Test
    @DisplayName("zoningCount is the per-town Map<Zone, Integer> SoT — private final, the type-erased Map")
    void zoningCountFieldIsMapOfZoneToInteger() throws Exception {
        Field field = Town.class.getDeclaredField("zoningCount");

        assertNotNull(field, "the zoningCount field must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPrivate(field.getModifiers()),
                "the SoT field stays private — mutators gate the writes"),
            () -> assertTrue(Modifier.isFinal(field.getModifiers()),
                "the SoT field is final — the reference never rebinds; the map mutates in place"),
            () -> assertTrue(Modifier.isStatic(field.getModifiers()) == false,
                "the SoT field is per-instance (Town state) — not a static cache"),
            () -> assertSame(Map.class, field.getType(),
                "the field type is Map (raw at the JVM level via type erasure); the generic"
                    + " Map<Zone, Integer> the addZoning mutator depends on is erased to Map")
        );
    }

    @Test
    @DisplayName("plannedRoads is the per-town List<RoadSegment> SoT — private final, the type-erased List")
    void plannedRoadsFieldIsListOfRoadSegment() throws Exception {
        Field field = Town.class.getDeclaredField("plannedRoads");

        assertNotNull(field, "the plannedRoads field must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isPrivate(field.getModifiers()),
                "the SoT field stays private — mutators gate the writes"),
            () -> assertTrue(Modifier.isFinal(field.getModifiers()),
                "the SoT field is final — the reference never rebinds; the list appends in place"),
            () -> assertTrue(Modifier.isStatic(field.getModifiers()) == false,
                "the SoT field is per-instance (Town state) — not a static cache"),
            () -> assertSame(List.class, field.getType(),
                "the field type is List (raw at the JVM level via type erasure); the generic"
                    + " List<RoadSegment> the addRoadSegment mutator depends on is erased to List")
        );
    }

    @Test
    @DisplayName("industryZoned() and roadLaid() are package-private — the SoT-shape derivations the read site consumes")
    void derivationsArePackagePrivate() throws Exception {
        Method industry = Town.class.getDeclaredMethod("industryZoned");
        Method road = Town.class.getDeclaredMethod("roadLaid");

        assertAll(
            () -> assertTrue(isPackagePrivate(industry.getModifiers()),
                "industryZoned is package-private — the structuralFlags() method in the same"
                    + " package reads it; outside callers go through structuralFlags()"),
            () -> assertEquals(boolean.class, industry.getReturnType(),
                "industryZoned returns boolean — the strict-derivation floor on a fresh save is false"),
            () -> assertTrue(isPackagePrivate(road.getModifiers()),
                "roadLaid is package-private — the structuralFlags() method in the same"
                    + " package reads it; outside callers go through structuralFlags()"),
            () -> assertEquals(boolean.class, road.getReturnType(),
                "roadLaid returns boolean — the strict-derivation floor on a fresh save is false")
        );
    }

    // True iff the modifier set has no public/protected/private bit set —
    // i.e. package-private (the default visibility in Java). Equivalent
    // to `Modifier.isPackagePrivate(modifiers)` (Java 9+), reproduced
    // here so the test class compiles against the project's pinned JDK.
    private static boolean isPackagePrivate(int mods) {
        return (mods & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) == 0;
    }
}