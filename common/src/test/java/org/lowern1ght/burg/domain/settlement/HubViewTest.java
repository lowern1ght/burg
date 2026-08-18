package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-shape {@link HubView} value object, in pure JUnit. Bare JVM,
 * no Minecraft — the same discipline {@code StockLedgerTest} and
 * {@code ConstructionQueueTest} use. The HubView is the additive
 * default for every world saved before the act-4 carve: a town with
 * no construction queue or an acquisition outside the act-4 set reads
 * as {@link #EMPTY}.
 */
class HubViewTest {

    @Test
    @DisplayName("EMPTY is the additive default for old saves and is referentially stable")
    void emptyIsTheDefault() {
        assertAll(
            () -> assertSame(HubView.EMPTY, HubView.EMPTY, "EMPTY is referentially stable"),
            () -> assertTrue(HubView.EMPTY.isEmpty(), "EMPTY.isEmpty() is true"),
            () -> assertSame(HubMode.CONSTRUCTION, HubView.EMPTY.mode(),
                "EMPTY carries CONSTRUCTION — the additive default")
        );
    }

    @Test
    @DisplayName("a non-empty HubView carries its mode and reports non-empty")
    void nonEmptyCarriesMode() {
        HubView view = new HubView(HubMode.SUPPLY);

        assertAll(
            () -> assertFalse(view.isEmpty(), "isEmpty() is false for a constructed mode"),
            () -> assertSame(HubMode.SUPPLY, view.mode(), "the mode is preserved"),
            () -> assertNotSame(HubView.EMPTY, view, "EMPTY is not reused for SUPPLY")
        );
    }

    @Test
    @DisplayName("two HubViews with the same mode are equal (record equality)")
    void recordEquality() {
        HubView constructionA = new HubView(HubMode.CONSTRUCTION);
        HubView constructionB = new HubView(HubMode.CONSTRUCTION);
        HubView supply = new HubView(HubMode.SUPPLY);

        assertAll(
            () -> assertEquals(constructionA, constructionB,
                "records with the same components are equal"),
            () -> assertFalse(constructionA.equals(supply),
                "different modes are not equal"),
            () -> assertFalse(constructionA.isEmpty(),
                "a CONSTRUCTION HubView constructed directly is not the EMPTY sentinel — "
                    + "EMPTY is the static reference, not all CONSTRUCTION views")
        );
    }

    @Test
    @DisplayName("HubMode.CONSTRUCTION is the additive default")
    void constructionIsDefault() {
        assertAll(
            () -> assertTrue(HubMode.CONSTRUCTION.isDefault()),
            () -> assertFalse(HubMode.SUPPLY.isDefault())
        );
    }
}