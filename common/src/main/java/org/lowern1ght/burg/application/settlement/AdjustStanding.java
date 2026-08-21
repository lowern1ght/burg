package org.lowern1ght.burg.application.settlement;

import org.lowern1ght.burg.application.settlement.ports.TownStandingPort;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.Objects;

/**
 * Use case: adjust one citizen's standing in one town (ADR-0014).
 *
 * @param citizen the citizen whose standing is being adjusted; never null
 * @param delta signed standing delta — may be negative (standing can fall)
 *
 * <p>The command is an immutable record over domain types only; the
 * {@link Handler} orchestrates through {@link TownStandingPort} and never
 * touches {@code Town} or any Minecraft type. This is the application-layer
 * seam every future standing caller (the act-2 greeting bonus, the act-4
 * threshold check, quest rewards) goes through instead of growing another
 * direct {@code Town.adjustStanding(UUID, int)} call site.
 *
 * <p>Delta may be negative — standing can fall. A score that lands back on
 * {@link Standing#DEFAULT} drops off the persisted roll (the sparse-book
 * discipline of {@code StandingBook}).
 */
public record AdjustStanding(CitizenId citizen, int delta) {

    /** Compact constructor — validates {@code citizen} is non-null. */
    public AdjustStanding {
        Objects.requireNonNull(citizen, "citizen");
    }

    /**
     * Executes the command against a town. Stateless — safe to share.
     */
    public static final class Handler {

        private final TownStandingPort town;

        /**
         * @param town the town standing port this handler writes through; never null
         */
        public Handler(TownStandingPort town) {
            this.town = town;
        }

        /**
         * Applies the adjustment and returns the citizen's resulting
         * standing (the roll's post-adjustment read, not a precomputed
         * value — the port remains the single source of truth).
         *
         * @param command the standing adjustment to apply; never null
         * @return the citizen's standing after the adjustment
         */
        public Standing handle(AdjustStanding command) {
            town.adjustStanding(command.citizen(), command.delta());
            return town.standingFor(command.citizen());
        }
    }
}
