package org.lowern1ght.burg.application.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.application.settlement.ports.TownStockPort;
import org.lowern1ght.burg.domain.settlement.StockLedger;
import org.lowern1ght.burg.domain.shared.ItemId;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link SupplyStock} use case against an in-memory fake port —
 * pure JUnit, no Minecraft (ADR-0014). Validation cases assert the port is
 * left untouched: a bad command must fail before any stock moves.
 */
class SupplyStockTest {

    private static final ItemId OAK_LOG = ItemId.of("minecraft:oak_log");

    @Test
    @DisplayName("supply deposits through the port")
    void supplyAdds() {
        FakeTownStock town = new FakeTownStock();
        SupplyStock.Handler handler = new SupplyStock.Handler(town);

        handler.handle(new SupplyStock(OAK_LOG, 10));

        assertEquals(10, town.stock().get(OAK_LOG));
    }

    @Test
    @DisplayName("repeated supplies accumulate")
    void suppliesAccumulate() {
        FakeTownStock town = new FakeTownStock();
        SupplyStock.Handler handler = new SupplyStock.Handler(town);

        handler.handle(new SupplyStock(OAK_LOG, 10));
        handler.handle(new SupplyStock(OAK_LOG, 32));

        assertEquals(42, town.stock().get(OAK_LOG));
    }

    @Test
    @DisplayName("zero quantity fails fast and moves no stock")
    void zeroQuantityRejected() {
        FakeTownStock town = new FakeTownStock();

        assertThrows(IllegalArgumentException.class,
            () -> new SupplyStock(OAK_LOG, 0));

        assertAll(
            () -> assertEquals(0, town.stock().get(OAK_LOG),
                "the port is untouched by the rejected command"),
            () -> assertEquals(0, town.stock().size())
        );
    }

    @Test
    @DisplayName("negative quantity fails fast and moves no stock")
    void negativeQuantityRejected() {
        FakeTownStock town = new FakeTownStock();

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> new SupplyStock(OAK_LOG, -5));

        assertAll(
            () -> thrown.getMessage().contains("positive"),
            () -> assertEquals(0, town.stock().get(OAK_LOG),
                "the port is untouched by the rejected command")
        );
    }

    @Test
    @DisplayName("a null item fails fast at the command boundary")
    void nullItemRejected() {
        assertThrows(NullPointerException.class,
            () -> new SupplyStock(null, 5));
    }

    @Test
    @DisplayName("supplying distinct items leaves each on its own slot")
    void distinctItemsKeepSeparateSlots() {
        FakeTownStock town = new FakeTownStock();
        SupplyStock.Handler handler = new SupplyStock.Handler(town);

        handler.handle(new SupplyStock(OAK_LOG, 10));
        handler.handle(new SupplyStock(ItemId.of("minecraft:stone"), 32));

        assertAll(
            () -> assertEquals(10, town.stock().get(OAK_LOG)),
            () -> assertEquals(32, town.stock().get(ItemId.of("minecraft:stone"))),
            () -> assertEquals(2, town.stock().size())
        );
    }

    @Test
    @DisplayName("a maximum-quantity supply is accepted at the command boundary")
    void maxQuantityAccepted() {
        FakeTownStock town = new FakeTownStock();
        SupplyStock.Handler handler = new SupplyStock.Handler(town);

        // Pin the upper-bound contract: Integer.MAX_VALUE is a positive
        // quantity and the command boundary lets it through. The ledger
        // itself is int-keyed — summing two MAX_VALUEs wraps, which is
        // the model's behaviour, not the boundary's.
        handler.handle(new SupplyStock(OAK_LOG, Integer.MAX_VALUE));

        assertEquals(Integer.MAX_VALUE, town.stock().get(OAK_LOG),
            "a single MAX_VALUE deposit reads back exactly as supplied");
    }

    @Test
    @DisplayName("the handler rethrows a port failure without partial mutation")
    void portFailurePropagates() {
        TownStockPort brokenPort = new TownStockPort() {
            @Override
            public StockLedger stock() {
                throw new IllegalStateException("port dead");
            }

            @Override
            public boolean tryTake(ItemId item, int quantity) {
                return false;
            }

            @Override
            public void add(ItemId item, int quantity) {
                throw new IllegalStateException("port dead");
            }
        };
        SupplyStock.Handler handler = new SupplyStock.Handler(brokenPort);

        assertThrows(IllegalStateException.class,
            () -> handler.handle(new SupplyStock(OAK_LOG, 5)),
            "a failing port surfaces its exception — the use case never swallows it");
    }
}
