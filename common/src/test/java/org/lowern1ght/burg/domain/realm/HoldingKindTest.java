package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three kinds of realm member. The load-bearing distinction is the
 * 2026-07-31 grilling correction: a realm is a capital plus its supply
 * network, not a bag of equal villages — homeNetwork vs periphery.
 */
class HoldingKindTest {

    @Test
    @DisplayName("exactly the three named kinds exist")
    void threeKinds() {
        var values = HoldingKind.values();
        assertEquals(3, values.length);
        assertArrayEquals(
            new HoldingKind[]{HoldingKind.METROPOLIS, HoldingKind.COLONY, HoldingKind.FOREIGN},
            values);
    }

    @Test
    @DisplayName("the home network is the metropolis plus its colonies")
    void homeNetworkIsSpine() {
        assertTrue(HoldingKind.METROPOLIS.isHomeNetwork());
        assertTrue(HoldingKind.COLONY.isHomeNetwork());
        assertFalse(HoldingKind.FOREIGN.isHomeNetwork());
    }
}
