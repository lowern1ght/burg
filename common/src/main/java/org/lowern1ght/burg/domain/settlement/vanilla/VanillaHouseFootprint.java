package org.lowern1ght.burg.domain.settlement.vanilla;

import java.util.Objects;

/**
 * A vanilla house position, projected into the Settlement bounded context.
 *
 * <p>The {@code BlockPos} a Minecraft {@code POI} manager hands us lives in
 * {@code net.minecraft.core}, which the domain layer is forbidden to import
 * (ADR-0008 §"Layers inside each context"). {@code VanillaHouseFootprint}
 * is the bare-JDK shape: three {@code int}s and equality on the triple.
 *
 * <p>Two footprints are equal iff their X, Y and Z components are all equal.
 * The record is immutable; the {@code Town.bindToVanillaVillage} facade
 * builds a set of these from the POI scan and hands the set to
 * {@link VanillaBindingDecider#decide} for the bare-JVM-decision half of
 * the conversion flow.
 */
public record VanillaHouseFootprint(int x, int y, int z) {

    public VanillaHouseFootprint {
        // No validation: vanilla house positions are world-block coordinates
        // and the engine does not constrain their range. A null footprint is
        // not meaningful here, but the contract is "any three ints go".
        Objects.requireNonNull(x, "x");
    }

    /**
     * Returns the squared XZ distance from this footprint to {@code (px, pz)}.
     * The decider uses XZ-distance only — the meeting point of a vanilla
     * village is a surface concept and the Y of a candidate anchor does
     * not decide whether the candidate sits inside the village.
     */
    public int squaredXzDistanceTo(int px, int pz) {
        long dx = (long) x - (long) px;
        long dz = (long) z - (long) pz;
        return (int) Math.min(Integer.MAX_VALUE, dx * dx + dz * dz);
    }
}