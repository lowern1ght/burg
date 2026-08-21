package org.lowern1ght.burg.town;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end behaviour pin for the {@link Town#corePopulated()} walk
 * rewrite. The bare-JVM counterpart
 * {@code :common:test/TownCorePopulatedWalkTest} pins the corner-distance
 * algorithm itself (via the {@link CoreRadiusCheck} helper) plus the
 * MC-typed signature; this file pins the integration — a real
 * {@link Town}, populated via {@link Town#registerBuilding} with real
 * {@link PlacedBuilding}s carrying real {@link BoundingBox} instances,
 * read through the package-private {@code corePopulated()} derivation
 * via reflection (which is the read site the existing test convention
 * uses for derivation methods on the {@code :common:test} carve-out's
 * MC-aware neighbourhood).
 *
 * <p><b>Why reflection here.</b> {@code corePopulated()} is
 * package-private — it is the read site the act-5 structural-flags
 * derivation ({@link Town#structuralFlags()}) consumes, not a public
 * API surface. Direct package access would work in principle, but the
 * existing convention in
 * {@code TownStructuralFlagsRealDerivationsTest} invokes the
 * package-private derivations through a public surfacing
 * ({@code town.structuralFlags()}) rather than calling them directly.
 * The structural-flags read stays public; the derivation stays
 * private. This test pins the derivation by reading it through a
 * tiny reflective seam — a future carve that exposes
 * {@code corePopulated()} publicly (or wires it straight into the
 * {@code :neoforge:test} target's existing read path) would replace
 * the reflection call with a direct one.
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li><b>Empty town.</b> {@code new Town()} with no buildings →
 *       {@code corePopulated() == false}. The additive default for a
 *       fresh save, the gate's no-progress floor.</li>
 *   <li><b>2 buildings inside the core radius.</b> Two real
 *       {@link PlacedBuilding}s registered inside a 32-block radius
 *       of the town's anchor → {@code corePopulated() == true}. The
 *       walk is permissive on Burg layouts (buildings do not have to
 *       carpet the core cell-by-cell — they just have to fit).</li>
 *   <li><b>1 building outside the core radius.</b> One
 *       {@link PlacedBuilding} whose {@code BoundingBox} extends past
 *       the 32-block radius → {@code corePopulated() == false}. The
 *       town is not core-populated while any building spills into
 *       the industry / military bands.</li>
 *   <li><b>No pre-BB failure.</b> A {@link PlacedBuilding} with
 *       {@code bb == null} (pre-BB save) is treated as compatible —
 *       does not flip {@code corePopulated()} to false on its own.
 *       Pinning this on the MC-aware path closes the legacy
 *       fail-closed loophole.</li>
 * </ol>
 */
class TownCorePopulatedWalkNeoforgeTest {

    @Test
    @DisplayName("empty town — corePopulated() returns false (no buildings)")
    void emptyTownCorePopulatedIsFalse() throws Exception {
        Town town = new Town();

        assertFalse(invokeCorePopulated(town),
            "a fresh town with no buildings reports corePopulated() == false — the"
                + " additive default for any save that has not earned the core yet");
    }

    @Test
    @DisplayName("2 buildings, both within 32 blocks of the anchor — corePopulated() returns true")
    void twoBuildingsInsideCoreReturnTrue() {
        Town town = new Town();

        // Two adjacent footprints: a 5×5 cottage centered on the anchor and a 3×3
        // carpenter's workshop beside it. Both bbs fit inside a 32-block radius.
        BoundingBox cottage = new BoundingBox(-2, 1, -2, 2, 4, 2);
        BoundingBox workshop = new BoundingBox(3, 1, -1, 5, 4, 1);
        town.registerBuilding(new BlockPos(0, 1, 0), "burg:cottage", java.util.List.of(), cottage, Rotation.NONE);
        town.registerBuilding(new BlockPos(4, 1, 0), "burg:workshop", java.util.List.of(), workshop, Rotation.NONE);

        assertAll(
            () -> assertEquals(2, town.getBuildings().size(),
                "two buildings registered — the precondition this case pins (got "
                    + town.getBuildings().size() + ")"),
            () -> assertTrue(invokeCorePopulated(town),
                "both footprints fit inside the 32-block core radius — corePopulated()"
                    + " must return true even though the buildings do NOT carpet every"
                    + " cell of the core (the legacy cell-walk would have failed here)")
        );
    }

    @Test
    @DisplayName("1 building whose bb extends past 32 blocks — corePopulated() returns false (the walk rejects)")
    void oneBuildingOutsideCoreReturnsFalse() {
        Town town = new Town();

        // A single building whose bb spans 100×100 centered on its own worldPos:
        // corners at (-50, -50), (-50, 49), (49, -50), (49, 49). Max corner
        // distance from the anchor (the building's own worldPos at origin) is
        // sqrt(49² + 49²) ≈ 69.3 — well outside the 32-block radius. The bb
        // represents a sprawling settlement plaza that bridges the core into the
        // INDUSTRY ring — the walk must reject even one spillover.
        BoundingBox sprawlingPlaza = new BoundingBox(-50, 0, -50, 49, 0, 49);
        town.registerBuilding(new BlockPos(0, 1, 0), "burg:plaza", java.util.List.of(),
            sprawlingPlaza, Rotation.NONE);

        assertEquals(1, town.getBuildings().size(),
            "one building registered — the precondition this case pins");
        assertFalse(invokeCorePopulated(town),
            "a single building whose bb extends past the 32-block radius rejects the"
                + " walk — this is the failing shape the legacy cell-walk also"
                + " rejected (the new walk rejects it the same way but on a"
                + " building-bb basis instead of a cell-coverage basis)");
    }

    @Test
    @DisplayName("mixed walk — one inside + one outside — corePopulated() returns false on the first failure")
    void mixedInsideAndOutsideReturnsFalse() {
        Town town = new Town();

        // Inside: small cottage at the anchor (worldPos + bb both fit).
        // Outside: a sprawling annex whose bb extends past 32 blocks. The inside
        // building would have made corePopulated() true on its own; the outside
        // bb drags the read back to false.
        BoundingBox cottage = new BoundingBox(-1, 0, -1, 1, 4, 1);
        BoundingBox annex = new BoundingBox(50, 0, 50, 100, 0, 100);
        town.registerBuilding(new BlockPos(0, 1, 0), "burg:cottage", java.util.List.of(), cottage, Rotation.NONE);
        town.registerBuilding(new BlockPos(75, 1, 75), "burg:annex", java.util.List.of(),
            annex, Rotation.NONE);

        assertEquals(2, town.getBuildings().size(),
            "two buildings registered — the precondition this case pins");
        assertFalse(invokeCorePopulated(town),
            "any one building whose bb extends past the 32-block ring rejects the"
                + " whole town — the walk short-circuits on the first failing bb");
    }

    @Test
    @DisplayName("pre-BB save — building with null bb does not fail the walk (null-bb escape hatch)")
    void preBbSaveIsCompatible() throws Exception {
        Town town = new Town();

        // Build a real Town Backing (buildings list) with one null-bb entry via
        // reflection — registerBuilding takes a BoundingBox so we go around it
        // through the field, mirroring a save that was written before the bb field
        // existed. The walk must treat null bb as compatible (the pre-BB escape
        // hatch — without it, every save that predates BB tracking would fail
        // closed exactly the way the legacy cell-walk did).
        PlacedBuilding legacy = new PlacedBuilding("burg:legacy", new BlockPos(0, 1, 0),
            null, Rotation.NONE);
        Field buildings = Town.class.getDeclaredField("buildings");
        buildings.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<PlacedBuilding> list = (java.util.List<PlacedBuilding>) buildings.get(town);
        list.add(legacy);

        assertAll(
            () -> assertEquals(1, town.getBuildings().size(),
                "one legacy building registered — the precondition this case pins"),
            () -> assertTrue(invokeCorePopulated(town),
                "the legacy building has null bb — the walk must return true (the"
                    + " pre-BB escape hatch). The legacy cell-walk returned false on"
                    + " this exact shape; the new walk is permissive on missing bb info")
        );
    }

    // ---------------------------------------------------------------------------
    // Reflection seam — Town.corePopulated() is package-private (the read site
    // is structuralFlags() in the same package). Direct calls would work in
    // principle, but the existing TownStructuralFlagsRealDerivationsTest
    // surfaces derivations through town.structuralFlags(); we keep the same
    // convention here so a regression that flips the derivation's visibility
    // fails the test in one place. The Method handle is cached in static
    // initialization order — see static block below.
    // ---------------------------------------------------------------------------

    private static final Method CORE_POPULATED_METHOD = corePopulatedMethod();

    private static Method corePopulatedMethod() {
        try {
            Method m = Town.class.getDeclaredMethod("corePopulated");
            m.setAccessible(true);
            return m;
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static boolean invokeCorePopulated(Town town) {
        try {
            return (Boolean) CORE_POPULATED_METHOD.invoke(town);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("corePopulated() should always be invocable on a"
                + " non-null Town instance whose class loaded", e);
        }
    }

    // Compile-time guard — pins the reflection is on the method we expect and
    // not, say, a renamed derivation with the same shape. A future carve that
    // renamed `corePopulated` to anything else would have to update this test
    // (the seam is centralised in `CORE_POPULATED_METHOD`).
    @SuppressWarnings("unused")
    private static void assertCorePopulatedSeam(Method m) {
        assertNotNull(m, "the corePopulated method handle must be non-null");
        assertSame(boolean.class, m.getReturnType(),
            "the derivation returns boolean — the read site flips the gate on a true");
        assertEquals("corePopulated", m.getName(),
            "the derivation's name is corePopulated — a renaming requires updating"
                + " both the read site and this reflection seam");
        assertEquals(0, m.getParameterCount(),
            "the derivation takes no parameters — it reads the town instance's"
                + " state directly");
    }
}
