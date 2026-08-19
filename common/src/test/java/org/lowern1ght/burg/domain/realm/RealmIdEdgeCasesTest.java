package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tester edge cases for {@link RealmId}. The trap here is the asymmetry
 * between the strict factory (trims, rejects blank) and the canonical
 * constructor (wraps verbatim): a caller that builds via {@code new}
 * gets a different value than one that builds via {@code of} from the
 * same raw string. These tests pin that asymmetry so it is a documented
 * decision, not a silent bug.
 */
class RealmIdEdgeCasesTest {

    @Test
    @DisplayName("of() rejects null before anything else")
    void ofRejectsNull() {
        assertThrows(NullPointerException.class, () -> RealmId.of(null));
    }

    @Test
    @DisplayName("of() trims outer whitespace but preserves inner whitespace")
    void trimBoundaries() {
        assertAll(
            () -> assertEquals("mercia", RealmId.of("  mercia  ").value(),
                "outer whitespace is trimmed"),
            () -> assertEquals("west sea", RealmId.of("  west sea  ").value(),
                "inner whitespace survives the trim — of() does not sanitise, only trims")
        );
    }

    @Test
    @DisplayName("tab- and newline-only strings are blank and rejected")
    void invisibleWhitespaceIsBlank() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of("\t")),
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of("\n")),
            () -> assertThrows(IllegalArgumentException.class, () -> RealmId.of(" \t \n "))
        );
    }

    @Test
    @DisplayName("String.trim only strips <= U+0020 — exotic whitespace survives as a real id")
    void exoticWhitespaceIsNotBlank() {
        // A zero-width space (U+200B) is whitespace to a human but not to
        // String.trim(). of() accepts it: the factory trims ASCII edges only.
        // Characterisation — if of() ever sanitises unicode, this changes.
        assertEquals("\u200B", RealmId.of("\u200B").value(),
            "the zero-width space is wrapped, not rejected");
    }

    @Test
    @DisplayName("the canonical constructor does NOT trim — new RealmId(x) and of(x) can differ")
    void canonicalConstructorDoesNotTrim() {
        RealmId viaNew = new RealmId("  mercia  ");
        RealmId viaOf = RealmId.of("  mercia  ");

        assertAll(
            () -> assertEquals("  mercia  ", viaNew.value(),
                "the canonical constructor wraps verbatim"),
            () -> assertEquals("mercia", viaOf.value(),
                "the strict factory trims"),
            () -> assertNotEquals(viaNew, viaOf,
                "the two construction paths produce different values for the same raw input")
        );
    }

    @Test
    @DisplayName("equality and hashCode agree across equal instances built by either factory")
    void equalityAcrossFactories() {
        RealmId left = RealmId.of("Wessex".toLowerCase(java.util.Locale.ROOT));
        RealmId right = new RealmId("wessex");

        assertAll(
            () -> assertEquals(left, right),
            () -> assertEquals(left.hashCode(), right.hashCode())
        );
    }

    @Test
    @DisplayName("long names and unicode survive the wrap — of() has no length or charset limit")
    void noLengthOrCharsetLimit() {
        String longName = "r".repeat(10_000);
        assertAll(
            () -> assertEquals(longName, RealmId.of(longName).value()),
            () -> assertEquals("Мирквуд", RealmId.of("Мирквуд").value(),
                "non-ASCII is not filtered — the id is a plain string wrapper")
        );
    }

    @Test
    @DisplayName("of(value()) round-trips — the canonical form is the wire form")
    void roundTrip() {
        RealmId id = RealmId.of("  danelaw  ");

        assertEquals(id, RealmId.of(id.value()),
            "re-of-ing the canonical value yields an equal id");
    }
}
