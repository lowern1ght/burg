package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link HoldingKind}: the home-network
 * partition is exactly {METROPOLIS, COLONY} — FOREIGN is the outsider.
 * Kills mutants like an {@code isHomeNetwork} that includes FOREIGN or
 * drops COLONY.
 */
class HoldingKindMutationTest {

    @Test
    @DisplayName("isHomeNetwork is true for the spine and false for the periphery")
    void homeNetworkPartition() {
        assertAll(
            () -> assertTrue(HoldingKind.METROPOLIS.isHomeNetwork(),
                "the capital grows from inside"),
            () -> assertTrue(HoldingKind.COLONY.isHomeNetwork(),
                "colonies are founded by expedition — spine"),
            () -> assertFalse(HoldingKind.FOREIGN.isHomeNetwork(),
                "acquired-from-outside towns are periphery (kills an includes-all mutant)")
        );
    }

    @Test
    @DisplayName("exactly three kinds exist — the union is closed")
    void exactlyThreeKinds() {
        assertEquals(3, HoldingKind.values().length,
            "METROPOLIS, COLONY, FOREIGN — a fourth kind is a domain decision");
    }

    @Test
    @DisplayName("valueOf round-trips each name — the persisted name layer is stable")
    void valueOfRoundTrip() {
        for (HoldingKind kind : HoldingKind.values()) {
            assertEquals(kind, HoldingKind.valueOf(kind.name()));
        }
    }
}
