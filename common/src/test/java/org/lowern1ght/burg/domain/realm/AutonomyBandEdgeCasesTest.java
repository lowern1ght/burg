package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link AutonomyBand}: the name-mapping edge is
 * where a persisted save meets the enum — whitespace, casing, and the
 * forward-compat default. Plus an exhaustive gate-exclusivity matrix:
 * every band answers exactly one "how is it controlled" question.
 */
class AutonomyBandEdgeCasesTest {

    @Test
    @DisplayName("fromAcquisitionName accepts any casing but no surrounding whitespace")
    void fromAcquisitionNameCasingAndWhitespace() {
        assertAll(
            () -> assertSame(AutonomyBand.ELEVATED, AutonomyBand.fromAcquisitionName("elevated"),
                "lowercase is upper-cased before lookup"),
            () -> assertSame(AutonomyBand.CAPTURED, AutonomyBand.fromAcquisitionName("Captured"),
                "mixed case is upper-cased before lookup"),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName(" CAPTURED"),
                "a leading space is NOT trimmed — the lookup misses and defaults to FREE"),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("CAPTURED "),
                "a trailing space is NOT trimmed either"),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("  "),
                "whitespace-only is not empty for the guard, but the lookup misses anyway")
        );
    }

    @Test
    @DisplayName("every band answers exactly one control gate — the gates are mutually exclusive")
    void gateExclusivityMatrix() {
        for (AutonomyBand band : AutonomyBand.values()) {
            int gates = 0;
            if (band.deafToOrders()) gates++;
            if (band.acceptsSoftOrders()) gates++;
            if (band.requiresGarrison()) gates++;
            assertEquals(1, gates,
                band + " must satisfy exactly one control gate (got " + gates + ")");
        }
    }

    @Test
    @DisplayName("the persisted acquisition names are the AutonomyBand names — wire round-trip for all four")
    void nameRoundTrip() {
        for (AutonomyBand band : AutonomyBand.values()) {
            assertSame(band, AutonomyBand.fromAcquisitionName(band.name()),
                band.name() + " round-trips through its own name");
        }
    }

    @Test
    @DisplayName("an Acquisition-only vocabulary word is not secretly an AutonomyBand")
    void settlementNamesAreNotBands() {
        // "ELEVATED"/"FOUNDED"/"CAPTURED" are shared with Acquisition; a
        // misspelling of one must fall to FREE, never to a near neighbour.
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("ELEVETED"),
            "a misspelling defaults to FREE, not ELEVATED");
    }

    @Test
    @DisplayName("FREE is reachable from any garbage — the forward-compat default is total")
    void freeDefaultIsTotal() {
        String[] garbage = {null, "", "FREE\u0000", "FREE FREE", "FRÉE", "free-ish"};
        for (String raw : garbage) {
            assertTrue(AutonomyBand.fromAcquisitionName(raw) == AutonomyBand.FREE,
                "fromAcquisitionName('" + raw + "') must be FREE");
        }
    }
}
