package org.lowern1ght.burg.application.settlement.ports;

import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;

/**
 * The application layer's window onto a town's stock (ADR-0014).
 *
 * <p>Use cases ({@code SupplyStock}, future budgeted-ledger flows) depend on
 * this interface — never on {@code town/Town.java} — so they stay
 * Minecraft-free and unit-testable against an in-memory fake. The
 * infrastructure adapter ({@code TownStockAdapter}) is the only
 * implementation that touches {@code Town}, resolving {@link ItemId} to a
 * Minecraft {@code Item} via {@code BuiltInRegistries} at the edge
 * (ADR-0008 §"Minecraft types leave the domain").
 *
 * <p><b>Read/write asymmetry (inherited from the strangler, ADR-0010).</b>
 * {@link #stock()} is the reserve-only {@link StockLedger} view; {@link #add}
 * and {@link #tryTake} operate on the town's aggregate inventory (per-building
 * stock plus reserve), matching the existing deposit path and the construction
 * queue's drain order. The carve that promotes the ledger to source of truth
 * (ADR-0010 non-goal 6.1) removes this asymmetry; until then the port
 * documents it rather than hides it.
 *
 * <p>No Minecraft imports (modding/AGENT-RULES.md rule 6).
 */
public interface TownStockPort {

    /**
     * Returns the town's reserve stock as a Minecraft-free
     * {@link StockLedger}, rebuilt from the legacy {@code reserveStock} map
     * on every call. Read-only.
     */
    StockLedger stock();

    /**
     * Atomically checks-and-drains {@code quantity} of {@code item} from the
     * town's aggregate inventory (per-building stock first, then reserve —
     * the same drain order the construction queue uses).
     *
     * @return {@code true} when the town held at least {@code quantity} and
     *         the stock was drained; {@code false} when it was not and the
     *         town is left untouched.
     */
    boolean tryTake(ItemId item, int quantity);

    /**
     * Deposits {@code quantity} of {@code item} into the town — the existing
     * player-deposit path (first building's storage if the town has
     * buildings, otherwise the floating reserve).
     */
    void add(ItemId item, int quantity);
}
