package org.lowern1ght.burg.application.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
}
