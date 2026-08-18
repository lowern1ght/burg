package org.lowern1ght.burg.application.settlement;

import org.lowern1ght.burg.application.settlement.ports.TownStockPort;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;

/**
 * In-memory fake of {@link TownStockPort} — the ledger is the real domain
 * object, so use-case tests exercise the same zero-drops-at-the-edge
 * semantics the future promotion carve will rely on. {@link #tryTake}
 * mirrors the port contract: check first, drain only on success. No
 * Minecraft.
 */
final class FakeTownStock implements TownStockPort {

    private StockLedger ledger = StockLedger.EMPTY;

    @Override
    public StockLedger stock() {
        return ledger;
    }

    @Override
    public boolean tryTake(ItemId item, int quantity) {
        if (quantity <= 0) return false;
        if (ledger.get(item) < quantity) return false;
        ledger = ledger.take(item, quantity);
        return true;
    }

    @Override
    public void add(ItemId item, int quantity) {
        ledger = ledger.add(item, quantity);
    }
}
