package org.lowern1ght.burg.town;

/**
 * Pure-int corner-distance check for the {@link Town#corePopulated()}
 * walk rewrite — the legacy cell-walk required every XZ cell inside
 * the 32-block core radius to be covered by at least one placed
 * building's {@link net.minecraft.world.level.levelgen.structure.BoundingBox
 * BoundingBox}, which fails closed on pre-BB saves (buildings whose
 * {@code bb} is {@code null}) and on towns whose buildings do not
 * carpet the entire core area. The new contract is "every building
 * bb fits inside the core radius", and this helper owns the per-bb
 * corner-distance math.
 *
 * <p><b>Why a separate class.</b> The class lives in its own file
 * (not on {@code Town}) and has no static fields, so its
 * {@code <clinit>} is empty and loading it does not require
 * {@code BoundingBox} or any other Minecraft type to be on the
 * test classpath. The bare-JVM {@code :common:test} target's
 * carve-out adds SLF4J + brigadier + datafixerupper + authlib
 * but not Netty / Mojang's logging facade, so
 * {@link net.minecraft.world.level.levelgen.structure.BoundingBox
 * BoundingBox}'s static init fails with
 * {@code NoClassDefFoundError}; {@code Town.class}'s static init
 * fails the same way via {@code ResourceLocation}. The pure-int
 * surface this class exposes is the path the bare-JVM test takes
 * — no MC static init involved — so the algorithm can be pinned
 * without the project-level constraint forcing a reflective
 * signature-only pin. The MC-typed overload that takes a
 * {@code BoundingBox} lives as a package-private static method on
 * {@link Town}; the end-to-end test (real {@link Town} + real
 * {@link PlacedBuilding}s) lives in {@code :neoforge:test}.
 *
 * <p><b>The algorithm.</b> For an axis-aligned
 * {@code BoundingBox}, the worst-case XZ distance from the anchor
 * to any cell of the bb is the distance to one of the four XZ
 * corners. Computing the maximum corner distance and comparing it
 * to {@code radius²} is therefore exact — the bb fits inside the
 * radius iff the furthest corner does. The boundary is inclusive:
 * a corner at exactly {@code radiusBlocks} distance is inside, a
 * corner at {@code radiusBlocks + 1} distance is outside. Negative
 * anchors are handled by the squared-distance check.
 */
final class CoreRadiusCheck {

    private CoreRadiusCheck() {}

    /**
     * True iff every XZ corner of the bounding box defined by
     * {@code (minX, minZ) .. (maxX, maxZ)} is within
     * {@code radiusBlocks} (inclusive) of {@code (anchorX, anchorZ)}.
     *
     * @param minX           the bb's minimum X (inclusive)
     * @param maxX           the bb's maximum X (inclusive)
     * @param minZ           the bb's minimum Z (inclusive)
     * @param maxZ           the bb's maximum Z (inclusive)
     * @param anchorX        the anchor's X coordinate
     * @param anchorZ        the anchor's Z coordinate
     * @param radiusBlocks   the radius in blocks (the gate's 32 for {@code Zone.CORE})
     * @return true iff every corner of the bb is within the radius
     */
    static boolean bbFitsInRadius(
        int minX, int maxX, int minZ, int maxZ,
        int anchorX, int anchorZ, int radiusBlocks
    ) {
        long rSq = (long) radiusBlocks * radiusBlocks;
        long maxR2 = 0;
        int[] xs = {minX, maxX};
        int[] zs = {minZ, maxZ};
        for (int x : xs) {
            for (int z : zs) {
                long dx = (long) x - anchorX;
                long dz = (long) z - anchorZ;
                long r2 = dx * dx + dz * dz;
                if (r2 > maxR2) maxR2 = r2;
            }
        }
        return maxR2 <= rSq;
    }
}
