package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mutation-style invariants for {@link RealmId}: the strict factory
 * trims and rejects blanks, and equality follows the trimmed value.
 * Kills mutants like a {@code trim} that is dropped or a blank check
 * that only tests {@code isEmpty()} on the raw input.
 */
class RealmIdMutationTest {

    @Test
    @DisplayName("of trims surrounding whitespace off the raw input")
    void ofTrims() {
        assertAll(
            () -> assertEquals("nord", RealmId.of("  nord  ").value(),
                "the canonical form is the trimmed input"),
            () -> assertEquals("nord", RealmId.of("nord").value())
        );
    }

    @Test
    @DisplayName("of rejects blank inputs — empty and whitespace-only")
    void ofRejectsBlanks() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of(""),
                "the empty string is not a realm"),
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of("   "),
                "whitespace-only trims to empty and is rejected"),
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of("\t\n "),
                "tabs and newlines are whitespace too"),
            () -> assertThrows(NullPointerException.class, () -> RealmId.of(null))
        );
    }

    @Test
    @DisplayName("equality follows the trimmed value — padded and bare ids are one realm")
    void equalityOnTrimmedValue() {
        assertEquals(new RealmId("nord"), RealmId.of(" nord "),
            "trim happens before the value is stored, so equality sees the same string");
    }

    @Test
    @DisplayName("the raw constructor rejects null but does not trim")
    void rawConstructorShape() {
        assertAll(
            () -> assertThrows(NullPointerException.class, () -> new RealmId(null)),
            () -> assertEquals(" padded ", new RealmId(" padded ").value(),
                "the raw constructor stores what it is given — of() is the trimming edge")
        );
    }
}
