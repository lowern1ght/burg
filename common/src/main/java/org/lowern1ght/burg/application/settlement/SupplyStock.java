package org.lowern1ght.burg.application.settlement;

import org.lowern1ght.burg.application.settlement.ports.TownStockPort;
import org.lowern1ght.burg.domain.shared.ItemId;

import java.util.Objects;

/**
 * Use case: supply items into a town's stock (ADR-0014).
 *
 * @param item the item being supplied; never null
 * @param quantity strictly positive number of units (enforced by the compact constructor)
 *
 * <p>The command is an immutable record over domain types only; the
 * {@link Handler} orchestrates through {@link TownStockPort} and never
 * touches {@code Town} or any Minecraft type. This is the application-layer
 * seam the {@code /town deposit} path and future supply flows (act-3
 * "supply steers what gets built") migrate onto, one call site per change.
 *
 * <p>Quantity must be strictly positive. A zero or negative supply is a
 * caller bug and fails fast at the command boundary — before the port is
 * touched — rather than being silently absorbed by the ledger.
 */
public record SupplyStock(ItemId item, int quantity) {

    public SupplyStock {
        Objects.requireNonNull(item, "item");
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "SupplyStock.quantity must be positive (got " + quantity + ")");
        }
    }

    /**
     * Executes the command against a town. Stateless — safe to share.
     */
    public static final class Handler {

        private final TownStockPort town;

        public Handler(TownStockPort town) {
            this.town = town;
        }

        /**
         * Deposits the supply through the port. Void by design — with the
         * real adapter the deposit lands in the first building's storage
         * while {@link TownStockPort#stock()} reads the reserve view, so a
         * read-back here would be wrong more often than right. Callers that
         * need the post-state read it from the port themselves.
         */
        public void handle(SupplyStock command) {
            town.add(command.item(), command.quantity());
        }
    }
}
