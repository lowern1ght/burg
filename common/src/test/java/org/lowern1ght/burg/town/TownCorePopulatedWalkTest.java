package org.lowern1ght.burg.town;

import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-JVM pin for the {@link Town#corePopulated()} walk rewrite — the
 * bug the legacy cell-walk had was that it required every XZ cell
 * inside the 32-block core radius to be covered by at least one
 * building's {@link BoundingBox}, which fails closed on two real-world
 * shapes:
 * <ol>
 *   <li>pre-BB saves — buildings whose {@code PlacedBuilding.bb} is
 *       {@code null} contribute nothing, so the walk returned false
 *       regardless of how many buildings the town actually had;</li>
 *   <li>towns whose buildings do not carpet the entire core area —
 *       e.g. a 4×4 farmstead centered on the anchor with crops
 *       beyond the building footprint, which is the natural Burg
 *       layout, not a vanilla village layout.</li>
 * </ol>
 *
 * <p>The fix changes the contract from "every core cell covered" to
 * "every building bb fits inside the core radius" and pushes the
 * per-bb corner-distance check into
 * {@link CoreRadiusCheck#bbFitsInRadius(int, int, int, int, int, int, int)}.
 * The class has no static fields and no MC imports, so loading it
 * does not require {@code BoundingBox} (whose static init pulls in
 * Mojang's logging facade) or any other MC type — exactly the
 * property the bare-JVM {@code :common:test} classpath needs.
 * The MC-typed overload that takes a {@link BoundingBox} is a
 * package-private static method on {@link Town}; the end-to-end
 * behaviour ({@link Town#corePopulated()} on a real {@link Town}
 * populated via {@link Town#registerBuilding}) is pinned in the
 * {@code :neoforge:test} target.
 *
 * <p><b>What this pins.</b>
 * <ol>
 *   <li>{@code CoreRadiusCheck.bbFitsInRadius(int, int, int, int,
 *       int, int, int)} is the bare-JVM-exercisable surface of the
 *       algorithm — {@code static}, {@code boolean},
 *       package-private, seven-int args
 *       {@code (minX, maxX, minZ, maxZ, anchorX, anchorZ, radiusBlocks)}.</li>
 *   <li>{@code Town.buildingInsideRadius(BoundingBox, int, int, int)}
 *       is the MC-typed entry point — {@code static}, {@code boolean},
 *       package-private, takes a (possibly null) {@code BoundingBox}
 *       plus the anchor XZ and radius. The null-bb escape hatch
 *       (pre-BB saves) lives on this overload; the bare-JVM signature
 *       for both is pinned but the null behaviour is reflected via
 *       this method's bytecode (no BoundingBox construction).</li>
 *   <li>The worst-corner-radius check is exact for an axis-aligned
 *       bb: a bb centered on the anchor is inside, a bb whose
 *       furthest corner is exactly on the boundary is inside
 *       (inclusive), a bb with any corner one block past the
 *       boundary is outside.</li>
 * </ol>
 */
class TownCorePopulatedWalkTest {

    @Test
    @DisplayName("CoreRadiusCheck.bbFitsInRadius exists with the right signature: package-private static boolean (int×7)")
    void bbFitsInRadiusSignatureIsRight() throws Exception {
        Method helper = CoreRadiusCheck.class.getDeclaredMethod(
            "bbFitsInRadius", int.class, int.class, int.class, int.class,
            int.class, int.class, int.class);

        assertNotNull(helper, "the corner-distance check must exist on CoreRadiusCheck");
        assertAll(
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "the helper is static — pure function of seven ints, no instance state needed"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "the helper returns boolean — true iff every corner is within radius²"),
            () -> assertEquals(7, helper.getParameterCount(),
                "the helper takes exactly seven ints: minX, maxX, minZ, maxZ, anchorX,"
                    + " anchorZ, radiusBlocks"),
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "the helper is package-private — the read site in Town#buildingInsideRadius()"
                    + " is in the same package; outside callers reach corePopulated() through"
                    + " structuralFlags()")
        );
    }

    @Test
    @DisplayName("Town.buildingInsideRadius exists with the right signature: package-private static boolean (BoundingBox, int, int, int)")
    void buildingInsideRadiusSignatureIsRight() throws Exception {
        Method helper = Town.class.getDeclaredMethod(
            "buildingInsideRadius", BoundingBox.class, int.class, int.class, int.class);

        assertNotNull(helper, "the MC-typed per-bb radius check must exist on Town");
        assertAll(
            () -> assertTrue(Modifier.isStatic(helper.getModifiers()),
                "the helper is static — a pure function of (bb, anchorX, anchorZ, radiusBlocks),"
                    + " no Town instance needed"),
            () -> assertEquals(boolean.class, helper.getReturnType(),
                "the helper returns boolean — inside (true) iff every cell of the bb is within"
                    + " radiusBlocks of the anchor"),
            () -> assertEquals(4, helper.getParameterCount(),
                "the helper takes exactly four parameters"),
            () -> assertSame(BoundingBox.class, helper.getParameterTypes()[0],
                "first parameter is BoundingBox — the building's footprint (null = pre-BB save)"),
            () -> assertEquals(int.class, helper.getParameterTypes()[1],
                "second parameter is int — the anchor's X coordinate"),
            () -> assertEquals(int.class, helper.getParameterTypes()[2],
                "third parameter is int — the anchor's Z coordinate"),
            () -> assertEquals(int.class, helper.getParameterTypes()[3],
                "fourth parameter is int — the radius in blocks (the gate's 32 for Zone.CORE)"),
            () -> assertTrue(isPackagePrivate(helper.getModifiers()),
                "the helper is package-private — the read site in Town#corePopulated() is in"
                    + " the same package; outside callers reach corePopulated() through"
                    + " structuralFlags()")
        );
    }

    @Test
    @DisplayName("bb fully inside the radius — every XZ cell well within 32 blocks of the anchor")
    void bbFullyInsideReturnsTrue() {
        // 4×4 bb centered at (0, 0): corners at (-2, -2), (-2, 1), (1, -2), (1, 1). Max
        // corner distance = sqrt(1² + 2²) = sqrt(5) ≈ 2.24 — well inside 32.
        assertTrue(CoreRadiusCheck.bbFitsInRadius(-2, 1, -2, 1, 0, 0, 32),
            "a 4×4 bb centered on the anchor is fully inside a 32-block radius");
    }

    @Test
    @DisplayName("bb with a corner exactly on the boundary — inclusive at radius")
    void bbAtBoundaryReturnsTrue() {
        // Single-cell bb with corner at (32, 0): distance = 32 — exactly the radius, the
        // gate must count it as inside (the boundary is inclusive, matching zoneOf()).
        assertTrue(CoreRadiusCheck.bbFitsInRadius(32, 32, 0, 0, 0, 0, 32),
            "a corner exactly at the radius is inside — the walk is inclusive at the boundary");
    }

    @Test
    @DisplayName("bb with a corner one block past the radius — outside, the walk rejects")
    void bbOneBlockPastBoundaryReturnsFalse() {
        // Single-cell bb at (33, 0): distance = 33 — one past the radius, must reject.
        assertFalse(CoreRadiusCheck.bbFitsInRadius(33, 33, 0, 0, 0, 0, 32),
            "a corner one block past the radius is outside — the walk must reject any"
                + " building whose footprint spills past the core ring");
    }

    @Test
    @DisplayName("bb with a corner far outside the radius — outside, the walk rejects")
    void bbFarOutsideReturnsFalse() {
        // 5×5 bb in the eastern INDUSTRY band: corners span (60..64, 60..64), max corner
        // distance = sqrt(64² + 64²) ≈ 90.5 — well outside 32.
        assertFalse(CoreRadiusCheck.bbFitsInRadius(60, 64, 60, 64, 0, 0, 32),
            "a 5×5 bb sitting in the eastern INDUSTRY band is outside the core radius —"
                + " even one corner past the ring is enough to reject");
    }

    @Test
    @DisplayName("off-centre bb — the worst-corner check tracks the furthest corner, not the centre")
    void bbOffCentreWorstCornerCheck() {
        // 3×3 bb sitting between the anchor and the +X ring: corners at (10, -1), (10, 1),
        // (12, -1), (12, 1). Max corner distance = sqrt(12² + 1²) = sqrt(145) ≈ 12.04 —
        // well inside 32. The bb is to the east of the anchor but its furthest corner
        // is still inside the ring.
        assertTrue(CoreRadiusCheck.bbFitsInRadius(10, 12, -1, 1, 0, 0, 32),
            "an off-centre bb whose furthest corner is still inside the ring returns true"
                + " — the check is the worst-corner distance, not the centre distance");
    }

    @Test
    @DisplayName("off-centre bb — the furthest corner pulls the bb outside the ring")
    void bbOffCentreClosestCornerUnderBoundary() {
        // 3×3 bb straddling the +X boundary: corners at (30, -1), (30, 1), (32, -1),
        // (32, 1). Max corner distance = sqrt(32² + 1²) ≈ 32.016 — JUST outside 32.
        // Even though (30, ...) is well inside, the (32, ...) corner is past the ring
        // and the bb does not fit; the walk rejects.
        assertFalse(CoreRadiusCheck.bbFitsInRadius(30, 32, -1, 1, 0, 0, 32),
            "the closest corners are well inside but the (32, ...) corner is just past"
                + " the ring — the bb does not fit, the walk rejects on the worst corner");
    }

    @Test
    @DisplayName("negative anchor coordinates — the radius check is sign-agnostic (uses squared distance)")
    void negativeAnchorCoordinatesAreHandled() {
        // Anchor at (-100, -100), 5×5 bb at (-102, -102) to (-98, -98). Max corner
        // distance from anchor = sqrt(2² + 2²) ≈ 2.83 — well inside 32.
        assertTrue(CoreRadiusCheck.bbFitsInRadius(-102, -98, -102, -98, -100, -100, 32),
            "negative anchors are handled by the squared-distance check — the bb is"
                + " inside the radius regardless of sign");
    }

    @Test
    @DisplayName("zero-radius edge — only a degenerate bb of the anchor's own cell fits")
    void zeroRadiusAnchorCell() {
        // radiusBlocks = 0: only the bb whose every corner is exactly on the anchor fits.
        assertTrue(CoreRadiusCheck.bbFitsInRadius(0, 0, 0, 0, 0, 0, 0),
            "a single-cell bb at the anchor fits when radius = 0 — the bb and the anchor"
                + " occupy the same single XZ cell");
        assertFalse(CoreRadiusCheck.bbFitsInRadius(0, 0, 0, 1, 0, 0, 0),
            "any bb with a corner off the anchor's own cell rejects when radius = 0");
    }

    // True iff the modifier set has no public/protected/private bit set —
    // i.e. package-private (the default visibility in Java).
    private static boolean isPackagePrivate(int mods) {
        return (mods & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) == 0;
    }
}
