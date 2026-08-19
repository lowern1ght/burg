package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link HubView} record + {@code EMPTY} sentinel, in pure JUnit.
 * Bare JVM, no Minecraft — the view is the additive default for worlds
 * saved before the hub-becomes-window carve lands, and the predicate
 * (queue empty OR acquisition ∉ {ELEVATED, FOUNDED}) collapses to
 * {@code EMPTY} on any such town. Two correctness traps the tests are
 * explicitly here to catch: (1) the record constructor rejects null
 * modes rather than storing them; (2) {@link HubView#EMPTY} is
 * referentially stable, so equality checks elsewhere are cheap and
 * unchanged across drains.
 */
class HubViewTest {

    @Test
    @DisplayName("EMPTY is the additive default — mode = CONSTRUCTION")
    void emptyIsTheDefault() {
        HubView empty = HubView.EMPTY;
        assertAll(
            () -> assertSame(HubView.EMPTY, empty,
                "EMPTY is referentially stable (the additive default sentinel)"),
            () -> assertSame(HubMode.CONSTRUCTION, empty.mode(),
                "EMPTY's mode is CONSTRUCTION"),
            () -> assertTrue(empty.isConstruction(),
                "EMPTY is a construction-mode view"),
            () -> assertFalse(empty.isSupply(),
                "EMPTY is not a supply-mode view")
        );
    }

    @Test
    @DisplayName("a SUPPLY view is built from the SUPPLY mode")
    void supplyViewIsBuiltFromSupplyMode() {
        HubView view = new HubView(HubMode.SUPPLY);
        assertAll(
            () -> assertSame(HubMode.SUPPLY, view.mode()),
            () -> assertTrue(view.isSupply()),
            () -> assertFalse(view.isConstruction(),
                "a SUPPLY view is not a construction view"),
            () -> assertNotEquals(HubView.EMPTY, view,
                "a SUPPLY view is not the additive-default sentinel")
        );
    }

    @Test
    @DisplayName("a CONSTRUCTION view equals EMPTY but is not the same instance")
    void constructionViewEqualsEmpty() {
        HubView view = new HubView(HubMode.CONSTRUCTION);
        assertAll(
            () -> assertEquals(HubView.EMPTY, view,
                "record equality is by value, not by identity"),
            () -> assertNotEquals(HubView.EMPTY, new HubView(HubMode.SUPPLY),
                "a SUPPLY view is not equal to EMPTY")
        );
    }

    @Test
    @DisplayName("of(null) returns EMPTY, not a stored-null record")
    void ofNullReturnsEmpty() {
        HubView view = HubView.of(null);
        assertSame(HubView.EMPTY, view,
            "of(null) must return the EMPTY sentinel, not store a null mode");
    }

    @Test
    @DisplayName("of(SUPPLY) returns a SUPPLY view")
    void ofSupplyReturnsSupplyView() {
        HubView view = HubView.of(HubMode.SUPPLY);
        assertSame(HubMode.SUPPLY, view.mode());
        assertTrue(view.isSupply());
    }

    @Test
    @DisplayName("the record constructor rejects a null mode")
    void recordConstructorRejectsNullMode() {
        assertThrows(NullPointerException.class, () -> new HubView(null));
    }
}