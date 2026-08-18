package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The autonomy–control slider's four bands, in pure JUnit. The gates
 * mirror the VISION text verbatim: free is deaf, elevated/founded take
 * soft orders, captured obeys only under garrison.
 */
class AutonomyBandTest {

    @Test
    @DisplayName("only FREE is deaf to orders")
    void freeIsDeaf() {
        assertTrue(AutonomyBand.FREE.deafToOrders());
        assertFalse(AutonomyBand.ELEVATED.deafToOrders());
        assertFalse(AutonomyBand.FOUNDED.deafToOrders());
        assertFalse(AutonomyBand.CAPTURED.deafToOrders());
    }

    @Test
    @DisplayName("ELEVATED and FOUNDED accept soft orders")
    void softOrders() {
        assertFalse(AutonomyBand.FREE.acceptsSoftOrders());
        assertTrue(AutonomyBand.ELEVATED.acceptsSoftOrders());
        assertTrue(AutonomyBand.FOUNDED.acceptsSoftOrders());
        assertFalse(AutonomyBand.CAPTURED.acceptsSoftOrders());
    }

    @Test
    @DisplayName("only CAPTURED requires a garrison")
    void garrison() {
        assertFalse(AutonomyBand.FREE.requiresGarrison());
        assertFalse(AutonomyBand.ELEVATED.requiresGarrison());
        assertFalse(AutonomyBand.FOUNDED.requiresGarrison());
        assertTrue(AutonomyBand.CAPTURED.requiresGarrison());
    }

    @Test
    @DisplayName("acquisition names map one-to-one; unknown defaults to FREE")
    void fromAcquisitionName() {
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("FREE"));
        assertSame(AutonomyBand.ELEVATED, AutonomyBand.fromAcquisitionName("ELEVATED"));
        assertSame(AutonomyBand.FOUNDED, AutonomyBand.fromAcquisitionName("FOUNDED"));
        assertSame(AutonomyBand.CAPTURED, AutonomyBand.fromAcquisitionName("CAPTURED"));
    }

    @Test
    @DisplayName("absent or unknown acquisition names default to FREE (forward-compat)")
    void unknownDefaultsToFree() {
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName(null));
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName(""));
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("FOOBAR"));
        assertSame(AutonomyBand.FREE, AutonomyBand.fromAcquisitionName("free"));
    }
}
