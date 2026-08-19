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
 * Mutation-style invariants for {@link HubMode}: the NBT edge is a
 * fixpoint, the parse path collapses every garbage input to
 * {@link HubMode#CONSTRUCTION}, and the additive default is stable
 * across every value. Each assertion is written to kill a specific
 * mutant (a flipped comparison, a swallowed default, a widened bound),
 * not to mirror the Javadoc.
 */
class HubModeMutationTest {

    @Test
    @DisplayName("fromNbtOrDefault ∘ toNbt is a fixpoint — every value re-reads as itself")
    void nbtRoundTripIsFixpoint() {
        for (HubMode value : HubMode.values()) {
            HubMode reloaded = HubMode.fromNbtOrDefault(value.toNbt());
            assertSame(value, reloaded,
                "toNbt→fromNbtOrDefault must reproduce " + value + " exactly");
            // Second pass through the same wire is stable.
            assertSame(value, HubMode.fromNbtOrDefault(reloaded.toNbt()),
                "second pass through the wire is stable for " + value);
        }
    }

    @Test
    @DisplayName("null / empty / unrecognized NBT strings all collapse to CONSTRUCTION")
    void garbageNbtCollapsesToConstruction() {
        assertAll(
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault(null),
                "null string is the additive default"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault(""),
                "empty string is the additive default"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("banana"),
                "unknown value is the additive default"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("CONSTRUCT"),
                "prefix-garbage is not a value"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("CONSTRUCTION_SUPPLY"),
                "concat-garbage is not a value")
        );
    }

    @Test
    @DisplayName("fromNbtOrDefault is case-insensitive — lowercase persisted forms load")
    void lowercaseNbtLoads() {
        assertAll(
            () -> assertSame(HubMode.SUPPLY, HubMode.fromNbtOrDefault("supply")),
            () -> assertSame(HubMode.SUPPLY, HubMode.fromNbtOrDefault("Supply"),
                "mixed case loads — the parse upper-cases"),
            () -> assertSame(HubMode.CONSTRUCTION, HubMode.fromNbtOrDefault("construction"))
        );
    }

    @Test
    @DisplayName("the enum has exactly two values — no more, no fewer")
    void enumCardinalityIsTwo() {
        assertEquals(2, HubMode.values().length,
            "HubMode is a closed two-value enum (kills an add-a-constant mutant)");
    }

    @Test
    @DisplayName("ordinal positions are stable — CONSTRUCTION = 0, SUPPLY = 1")
    void ordinalsAreStable() {
        assertEquals(0, HubMode.CONSTRUCTION.ordinal());
        assertEquals(1, HubMode.SUPPLY.ordinal());
    }

    @Test
    @DisplayName("only CONSTRUCTION isDefault — SUPPLY knows it is not the default")
    void onlyConstructionIsDefault() {
        for (HubMode value : HubMode.values()) {
            assertEquals(value == HubMode.CONSTRUCTION, value.isDefault(),
                "isDefault must be true for CONSTRUCTION alone (got " + value + ")");
        }
    }

    @Test
    @DisplayName("toNbt emits the enum name — the wire form is uppercase and stable")
    void toNbtIsEnumName() {
        for (HubMode value : HubMode.values()) {
            assertEquals(value.name(), value.toNbt());
        }
    }

    @Test
    @DisplayName("toString never throws and is non-null for every mode")
    void toStringIsSafe() {
        for (HubMode value : HubMode.values()) {
            String text = value.toString();
            assertTrue(text != null && !text.isEmpty(),
                "toString of " + value + " is non-empty");
            assertNotEquals("null", text,
                "toString of " + value + " must not render as the literal string \"null\"");
        }
    }
}