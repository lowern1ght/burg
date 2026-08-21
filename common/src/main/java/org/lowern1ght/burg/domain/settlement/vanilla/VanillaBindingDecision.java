package org.lowern1ght.burg.domain.settlement.vanilla;

import java.util.Objects;
import java.util.Set;

/**
 * The decision produced by the vanilla-village conversion check, in pure
 * JUnit-testable shape.
 *
 * <p>Two outcomes, mirroring the user-visible behaviour of
 * {@code Town.bindToVanillaVillage}:
 *
 * <ul>
 *   <li>{@link Skip} — the candidate position is not at a vanilla village's
 *       meeting point. The {@code Town.bindToVanillaVillage} facade maps
 *       this to {@code return false}; the {@code TownAnchorBlock.use()}
 *       path falls through to today's hub-open behaviour.</li>
 *   <li>{@link Bind} — the candidate sits at a vanilla village's meeting
 *       point. The {@code Town.bindToVanillaVillage} facade maps this to
 *       "enlist villagers, mark houses as occupied footprints, place the
 *       bridgehead piece, return true".</li>
 * </ul>
 *
 * <p>Sealed so a future contributor adding a {@code Defer} or
 * {@code RequireMoreFootprints} variant is forced to update every test
 * that exhaustively matches on the type, instead of letting a third
 * outcome slip in untested.
 */
public sealed interface VanillaBindingDecision
        permits VanillaBindingDecision.Skip, VanillaBindingDecision.Bind {

    /**
     * Stable reason code — used by {@code Town.bindToVanillaVillage} to log
     * why a placement was rejected, and by callers that want to branch on
     * the kind of skip rather than its prose message.
     */
    String reasonCode();

    /**
     * The conversion does NOT apply at this position. {@code reasonCode}
     * is one of the {@code Skip.REASON_*} constants.
     *
     * @param reasonCode stable reason code (e.g. {@link Skip#REASON_NO_FOOTPRINTS}); never blank
     * @param detail human-readable explanation of the skip; never null
     */
    record Skip(String reasonCode, String detail) implements VanillaBindingDecision {

        /** No footprints were collected — empty POI set is the typical case for non-village coords. */
        public static final String REASON_NO_FOOTPRINTS = "no_vanilla_footprints";
        /** Footprints exist but none sit within the village radius of the candidate. */
        public static final String REASON_OUT_OF_RANGE = "vanilla_footprints_out_of_range";

        public Skip {
            Objects.requireNonNull(reasonCode, "reasonCode");
            Objects.requireNonNull(detail, "detail");
            if (reasonCode.isEmpty()) {
                throw new IllegalArgumentException("reasonCode must not be blank");
            }
        }

        /** Convenience factory for the {@link #REASON_NO_FOOTPRINTS} case. */
        public static Skip noFootprints() {
            return new Skip(REASON_NO_FOOTPRINTS, "no vanilla village footprints were collected");
        }

        /** Convenience factory for the {@link #REASON_OUT_OF_RANGE} case. */
        public static Skip outOfRange(int nearestFootprintXz) {
            return new Skip(REASON_OUT_OF_RANGE,
                "nearest footprint is " + nearestFootprintXz + " blocks away on XZ");
        }
    }

    /**
     * The conversion applies at this position. {@code footprints} is the
     * set the facade will register as blocked zones; the {@code town}
     * keeps the set around to make sure subsequent growth never lands a
     * Burg building on top of an existing vanilla house.
     *
     * @param footprints the blocked-zone set (non-empty, defensively copied); never null
     */
    record Bind(Set<VanillaHouseFootprint> footprints) implements VanillaBindingDecision {

        /** Stable reason code for the bind path; {@link Skip} varies by cause, Bind does not. */
        public static final String REASON_BOUND = "vanilla_bound";

        public Bind {
            Objects.requireNonNull(footprints, "footprints");
            if (footprints.isEmpty()) {
                throw new IllegalArgumentException(
                    "Bind decision must carry at least one footprint");
            }
            footprints = Set.copyOf(footprints);   // immutable snapshot
        }

        @Override
        public String reasonCode() {
            return REASON_BOUND;
        }
    }
}