package org.lowern1ght.burg.domain.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two-value {@link HubMode} enum, in pure JUnit. Bare JVM, no
 * Minecraft — the mode is the additive NBT default for worlds saved
 * before the hub-becomes-window carve lands, so the boundaries around
 * {@link HubMode#CONSTRUCTION} are exactly the kind of detail that is
 * silent in a unit round trip and noisy in the engine tick.
 */
class HubModeTest {

    @Test
    @DisplayName("the additive default for old saves is CONSTRUCTION")
    void oldSavesDefaultToConstruction() {
        assertAll(
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault(null),
                "null reads as CONSTRUCTION"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault(""),
                "empty string reads as CONSTRUCTION"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("FOOBAR"),
                "unknown names read as CONSTRUCTION (forward-compat with newer modes)"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("construction"),
                "lower-case is normalised before lookup")
        );
    }

    @Test
    @DisplayName("the two named values round-trip through NBT")
    void roundTrip() {
        for (HubMode mode : HubMode.values()) {
            assertSame(mode, HubMode.fromNbtOrDefault(mode.toNbt()),
                mode.name() + " survives the string round-trip");
        }
    }

    @Test
    @DisplayName("SUPPLY round-trips case-insensitively")
    void supplyRoundTripCaseInsensitive() {
        assertAll(
            () -> assertSame(HubMode.SUPPLY, HubMode.fromNbtOrDefault("supply")),
            () -> assertSame(HubMode.SUPPLY, HubMode.fromNbtOrDefault("Supply")),
            () -> assertSame(HubMode.SUPPLY, HubMode.fromNbtOrDefault("SUPPLY"))
        );
    }

    @Test
    @DisplayName("only CONSTRUCTION is the additive default")
    void onlyConstructionIsDefault() {
        assertAll(
            () -> assertTrue(HubMode.CONSTRUCTION.isDefault()),
            () -> assertFalse(HubMode.SUPPLY.isDefault())
        );
    }
}