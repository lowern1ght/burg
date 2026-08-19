package org.lowern1ght.burg.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tester edge cases for {@link ItemId} — the boundaries a player or a
 * datapack author can actually hit. Where {@code ItemIdTest} pins the
 * canonical-form happy path, this file tortures the strict factory with
 * hostile strings, pins the EMPTY sentinel's own namespace/path, and
 * verifies the lowercase normalisation holds as a hash-map key contract
 * (the whole point of normalising at the factory).
 */
class ItemIdEdgeCasesTest {

    @Test
    @DisplayName("of() rejects null, empty, and the hostile string shapes a datapack can contain")
    void ofRejectsHostileStrings() {
        assertAll(
            () -> assertThrows(NullPointerException.class, () -> ItemId.of(null),
                "null raw is rejected"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of(""),
                "empty string is rejected"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("   "),
                "whitespace-only is rejected (space is not a legal namespace char)"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("minecraft:oak log"),
                "a space inside the path is rejected"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("minecraft:oak^log"),
                "a caret is not a legal path character"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("mod$a:oak_log"),
                "a dollar sign is not a legal namespace character")
        );
    }

    @Test
    @DisplayName("a second colon is not a separator — the first colon wins and the rest must be a legal path")
    void secondColonIsPartOfPath() {
        // "mod:a:b" splits at the FIRST colon: namespace "mod", path "a:b".
        // ':' is not a legal path character, so the strict factory rejects it —
        // this pins that ItemId is namespace:path, not namespace:path:extra.
        assertThrows(IllegalArgumentException.class, () -> ItemId.of("mod:a:b"),
            "a colon inside the path is rejected");
    }

    @Test
    @DisplayName("EMPTY is the minecraft:air sentinel, not an empty string")
    void emptySentinelShape() {
        assertAll(
            () -> assertEquals("minecraft:air", ItemId.EMPTY.value(),
                "EMPTY is a real id, not a blank"),
            () -> assertEquals("minecraft", ItemId.EMPTY.namespace(),
                "EMPTY has a namespace like any other id"),
            () -> assertEquals("air", ItemId.EMPTY.path(),
                "EMPTY has a path like any other id"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("garbage"),
                "lenient parse failures reuse the sentinel instance")
        );
    }

    @Test
    @DisplayName("namespace()/path() on a colon-less canonical value degrade, never throw")
    void colonLessAccessors() {
        // The canonical constructor deliberately does not validate the shape
        // (the additive NBT load path must wrap whatever was persisted), so a
        // colon-less value can exist. Pin the documented degradation.
        ItemId colonLess = new ItemId("justakey");

        assertAll(
            () -> assertEquals("", colonLess.namespace(),
                "no colon ⇒ empty namespace"),
            () -> assertEquals("justakey", colonLess.path(),
                "no colon ⇒ the whole value is the path")
        );
    }

    @Test
    @DisplayName("the canonical constructor accepts shapes of() would reject — the NBT path relies on that")
    void canonicalConstructorIsDeliberatelyLenient() {
        // Characterisation: new ItemId(...) skips validation on purpose (the
        // class javadoc says so). If this ever starts throwing, the additive
        // NBT load path changed shape and every caller must be re-audited.
        ItemId weird = new ItemId("Not A:Resource Location");

        assertEquals("Not A:Resource Location", weird.value(),
            "the canonical constructor wraps whatever it is handed");
    }

    @Test
    @DisplayName("uppercase input normalises to the same hash-map key as lowercase input")
    void normalisationIsAHashMapKeyContract() {
        ItemId lower = ItemId.of("burg:OAK_LOG".toLowerCase(java.util.Locale.ROOT));
        ItemId upper = ItemId.of("BURG:OAK_LOG");

        Map<ItemId, Integer> map = new HashMap<>();
        map.put(lower, 7);

        assertAll(
            () -> assertEquals(lower, upper,
                "equal-but-differently-cased factory inputs are equal"),
            () -> assertEquals(lower.hashCode(), upper.hashCode(),
                "hashCode agrees with equals"),
            () -> assertEquals(7, map.get(upper),
                "a lookup with the other casing hits the same bucket — the whole point of normalising")
        );
    }

    @Test
    @DisplayName("equality separates values that differ only in namespace / path")
    void inequalityMatrix() {
        assertAll(
            () -> assertNotEquals(ItemId.of("burg:oak_log"), ItemId.of("minecraft:oak_log"),
                "same path, different namespace ⇒ unequal"),
            () -> assertNotEquals(ItemId.of("burg:oak_log"), ItemId.of("burg:oak_wood"),
                "same namespace, different path ⇒ unequal"),
            () -> assertNotEquals(ItemId.EMPTY, new ItemId("minecraft:air "),
                "a trailing space (buildable via the lenient ctor) makes a different value")
        );
    }

    @Test
    @DisplayName("of(value()) is the identity — rebuilding from the canonical form is stable")
    void rebuildFromCanonicalFormIsStable() {
        ItemId id = ItemId.of("Burg:Some_Path.v2/sub-item");

        ItemId rebuilt = ItemId.of(id.value());

        assertAll(
            () -> assertEquals(id, rebuilt),
            () -> assertEquals(id.hashCode(), rebuilt.hashCode()),
            () -> assertEquals(id.value(), rebuilt.value())
        );
    }

    @Test
    @DisplayName("repeated strict parses of the same hostile-ish input behave identically (100x)")
    void repeatedParseIsDeterministic() {
        ItemId first = null;
        for (int i = 0; i < 100; i++) {
            ItemId parsed = ItemId.of("Burg:Repeat_Me");
            if (first == null) {
                first = parsed;
            } else {
                assertEquals(first, parsed,
                    "every parse of the same input yields an equal value (iteration " + i + ")");
            }
        }
        assertEquals("burg:repeat_me", first.value(),
            "100 iterations did not change the canonical form");
    }

    @Test
    @DisplayName("namespace boundaries: dots and dashes are legal, a bare colon is not")
    void namespaceCharacterBoundaries() {
        assertAll(
            () -> assertEquals("m.c-donalds", ItemId.of("m.c-donalds:farm").namespace(),
                "dots and dashes are legal namespace characters"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("::x"),
                "an empty namespace is rejected however many colons follow")
        );
    }

    @Test
    @DisplayName("parseOrEmpty never throws — every hostile shape collapses to EMPTY")
    void parseOrEmptyNeverThrows() {
        String[] hostile = {
            null, "", "   ", "no-colon", ":leading", "trailing:", "sp ace:x", "x:sp ace",
            "a:b:c", "!!!:###"
        };
        for (String raw : hostile) {
            assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(raw),
                "parseOrEmpty('" + raw + "') must be EMPTY, never a throw");
        }
    }

    @Test
    @DisplayName("unicode LETTERS pass validation — broader than vanilla ResourceLocation")
    void unicodeLettersAreAccepted() {
        // Character.isLetterOrDigit says ü/ñ/ï are letters, so of() accepts
        // them. Vanilla ResourceLocation is ASCII-only, so a key round-tripped
        // through the real registry can never look like this — but the domain
        // factory will happily wrap one hand-built at the NBT edge.
        // Characterisation: if of() is ever tightened to ASCII, this flips.
        ItemId unicode = ItemId.of("üñï:x");

        assertEquals("üñï:x", unicode.value(),
            "unicode letters are legal namespace characters in the domain factory");
    }

    @Test
    @DisplayName("parseOrEmpty normalises valid input instead of sentinel-ing it")
    void parseOrEmptyNormalises() {
        ItemId parsed = ItemId.parseOrEmpty("MINECRAFT:STONE");

        assertAll(
            () -> assertNotEquals(ItemId.EMPTY, parsed),
            () -> assertEquals(ItemId.of("minecraft:stone"), parsed,
                "valid input is normalised, not just wrapped")
        );
    }

    @Test
    @DisplayName("null lookups are rejected — get-style boundaries stay strict")
    void nullBoundaries() {
        assertThrows(NullPointerException.class, () -> ItemId.of(null),
            "the strict factory rejects null before anything else");
    }
}
