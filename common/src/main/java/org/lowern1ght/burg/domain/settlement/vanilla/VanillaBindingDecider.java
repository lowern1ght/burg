package org.lowern1ght.burg.domain.settlement.vanilla;

import java.util.Objects;
import java.util.Set;

/**
 * Pure, bare-JVM decision function for "is this candidate position at the
 * meeting point of a vanilla village?".
 *
 * <p>The {@code Town.bindToVanillaVillage} facade collects a set of vanilla
 * house footprints via Minecraft's POI API and asks this decider what to
 * do. The decider itself has no Minecraft dependency — it operates on
 * {@link VanillaHouseFootprint} triples and a candidate XZ position — so
 * the {@code Skip} / {@link VanillaBindingDecision.Bind Bind} outcome is
 * unit-testable on a plain JVM without a running {@code ServerLevel}.
 *
 * <p><b>The "non-vanilla coords" contract.</b> When the facade calls
 * {@link #decide} with an empty footprint set, or with a non-empty set
 * whose nearest footprint sits further than {@code radius} on XZ from the
 * candidate, the decider MUST return
 * {@link VanillaBindingDecision.Skip}. The {@code Town.bindToVanillaVillage}
 * facade translates that Skip into {@code return false} — the anchor
 * placement then falls through to today's hub-open behaviour, and no
 * bridgehead piece is placed.
 *
 * <p>Why XZ-distance and not Euclidean? A vanilla village's meeting point
 * is a surface concept, and the Y of a candidate anchor depends on where
 * the player happens to stand when they place the block — it does not
 * decide whether the position is at a village. The decider matches the
 * spec wording: "within the vanilla village's POI radius" is a horizontal
 * criterion in vanilla itself.
 *
 * <p>The default radius (32 blocks) is the one vanilla uses to count a
 * house as belonging to a village; see
 * {@code net.minecraft.world.entity.ai.village.Village} in the engine
 * source.
 */
public final class VanillaBindingDecider {

    /**
     * Default village radius, in blocks. Matches vanilla's own
     * "this house belongs to this village" criterion, so a candidate
     * within 32 XZ-blocks of any collected house footprint counts as
     * being at the meeting point.
     */
    public static final int DEFAULT_RADIUS = 32;

    private final int radius;

    public VanillaBindingDecider() {
        this(DEFAULT_RADIUS);
    }

    public VanillaBindingDecider(int radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException(
                "village radius must be positive (got " + radius + ")");
        }
        this.radius = radius;
    }

    public int radius() {
        return radius;
    }

    /**
     * Decides whether {@code candidate} sits at the meeting point of one
     * of the vanilla villages represented by {@code footprints}.
     *
     * <p>Returns:
     * <ul>
     *   <li>{@link VanillaBindingDecision.Skip#noFootprints()} when
     *       {@code footprints} is empty — the typical "non-village
     *       coords" case.</li>
     *   <li>{@link VanillaBindingDecision.Skip#outOfRange(int)} when
     *       {@code footprints} is non-empty but every footprint is further
     *       than {@link #radius()} on XZ from {@code candidate}.</li>
     *   <li>{@link VanillaBindingDecision.Bind Bind} (carrying the
     *       full {@code footprints} set) when at least one footprint sits
     *       within {@link #radius()} on XZ of {@code candidate}.</li>
     * </ul>
     *
     * <p>The candidate coordinates are passed as plain {@code int}s
     * (not a {@code BlockPos}) on purpose: the decider is Minecraft-free
     * and the {@code Town} facade does the {@code BlockPos} →
     * {@code (int, int)} unwrap at the boundary.
     */
    public VanillaBindingDecision decide(Set<VanillaHouseFootprint> footprints,
                                         int candidateX, int candidateZ) {
        Objects.requireNonNull(footprints, "footprints");
        if (footprints.isEmpty()) {
            return VanillaBindingDecision.Skip.noFootprints();
        }
        int nearestXz = Integer.MAX_VALUE;
        boolean anyInRange = false;
        for (VanillaHouseFootprint fp : footprints) {
            int distSq = fp.squaredXzDistanceTo(candidateX, candidateZ);
            int dist = (int) Math.sqrt(distSq);
            if (dist < nearestXz) nearestXz = dist;
            if (dist <= radius) {
                anyInRange = true;
            }
        }
        if (!anyInRange) {
            return VanillaBindingDecision.Skip.outOfRange(nearestXz);
        }
        return new VanillaBindingDecision.Bind(footprints);
    }
}