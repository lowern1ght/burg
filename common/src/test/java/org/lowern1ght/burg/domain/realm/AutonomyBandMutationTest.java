package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.Acquisition;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link AutonomyBand}: the three gates
 * partition the bands without overlap or holes, the acquisition-name
 * edge defaults to FREE, and — crucially — the bands stay name-aligned
 * with {@code Acquisition} so the cross-context mapping is exhaustive.
 */
class AutonomyBandMutationTest {

    @Test
    @DisplayName("the gates partition the bands: deaf only FREE, soft ELEVATED+FOUNDED, garrison only CAPTURED")
    void gatePartition() {
        for (AutonomyBand band : AutonomyBand.values()) {
            assertEquals(band == AutonomyBand.FREE, band.deafToOrders(),
                "deafToOrders must hold for FREE alone (got " + band + ")");
            assertEquals(
                band == AutonomyBand.ELEVATED || band == AutonomyBand.FOUNDED,
                band.acceptsSoftOrders(),
                "acceptsSoftOrders must hold for ELEVATED and FOUNDED alone (got " + band + ")");
            assertEquals(band == AutonomyBand.CAPTURED, band.requiresGarrison(),
                "requiresGarrison must hold for CAPTURED alone (got " + band + ")");
        }
    }

    @Test
    @DisplayName("no band is ungoverned: every band trips at least one gate reading")
    void everyBandHasAMeaningfulReading() {
        // FREE: deaf; ELEVATED/FOUNDED: soft orders; CAPTURED: garrison.
        assertTrue(AutonomyBand.FREE.deafToOrders());
        assertTrue(AutonomyBand.ELEVATED.acceptsSoftOrders());
        assertTrue(AutonomyBand.FOUNDED.acceptsSoftOrders());
        assertTrue(AutonomyBand.CAPTURED.requiresGarrison());

        assertFalse(AutonomyBand.FREE.acceptsSoftOrders(),
            "FREE does not take soft orders");
        assertFalse(AutonomyBand.CAPTURED.acceptsSoftOrders(),
            "CAPTURED is not a soft-orders band — force only");
    }

    @Test
    @DisplayName("fromAcquisitionName: null / empty / unknown collapse to FREE")
    void fromAcquisitionNameDefaults() {
        assertAll(
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName(null)),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("")),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("banana")),
            () -> assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("freeburg"))
        );
    }

    @Test
    @DisplayName("fromAcquisitionName round-trips every band name, case-insensitively")
    void fromAcquisitionNameRoundTrip() {
        for (AutonomyBand band : AutonomyBand.values()) {
            assertSame(band, AutonomyBand.fromAcquisitionName(band.name()),
                "the enum name loads back as " + band);
            assertSame(band, AutonomyBand.fromAcquisitionName(band.name().toLowerCase(java.util.Locale.ROOT)),
                "lowercase persisted forms load too");
        }
    }

    @Test
    @DisplayName("the mapping is NOT lenient about whitespace — valueOf does not trim")
    void fromAcquisitionNameDoesNotTrim() {
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName(" captured"),
            "a padded name is unknown to valueOf, so it defaults (pins the no-trim edge)");
    }

    @Test
    @DisplayName("name alignment: every Acquisition NBT name lands on the like-named band")
    void alignedWithAcquisitionNames() {
        for (Acquisition acquisition : Acquisition.values()) {
            AutonomyBand mapped = AutonomyBand.fromAcquisitionName(acquisition.toNbt());
            assertEquals(acquisition.name(), mapped.name(),
                "the persisted acquisition " + acquisition + " maps onto the identically-named band");
        }
    }
}
