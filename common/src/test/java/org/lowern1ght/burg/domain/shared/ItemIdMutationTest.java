package org.lowern1ght.burg.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Mutation-style invariants for {@link ItemId}: strict factory
 * normalization (case-folding to the canonical form), malformed-shape
 * rejection, and the lenient edge used by the additive NBT load path.
 * Kills mutants like a validation that accepts a leading colon, or an
 * {@code of} that forgets to lowercase (breaking map-key equality).
 */
class ItemIdMutationTest {

    @Test
    @DisplayName("of normalizes case — MINECRAFT:OAK_LOG and minecraft:oak_log are the same key")
    void ofNormalizesCase() {
        assertAll(
            () -> assertEquals(ItemId.of("minecraft:oak_log"), ItemId.of("MINECRAFT:OAK_LOG"),
                "of() lowercases, so both forms are one canonical key"),
            () -> assertEquals("minecraft:oak_log", ItemId.of("MINECRAFT:OAK_LOG").value(),
                "the stored value is the normalized form")
        );
    }

    @Test
    @DisplayName("of rejects every malformed shape at the boundary")
    void ofRejectsMalformedShapes() {
        assertAll(
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of(""),
                "empty string has no colon"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("stone"),
                "a bare path with no namespace is rejected"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of(":stone"),
                "a leading colon means an empty namespace"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("minecraft:"),
                "a trailing colon means an empty path"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("mine craft:stone"),
                "a space is not a legal namespace char"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("minecraft:st one"),
                "a space is not a legal path char"),
            () -> assertThrows(IllegalArgumentException.class, () -> ItemId.of("minecraft:stone!"),
                "punctuation is not a legal path char"),
            () -> assertThrows(NullPointerException.class, () -> ItemId.of(null))
        );
    }

    @Test
    @DisplayName("of accepts the legal alphabet: underscore, hyphen, dot, slash in the path")
    void ofAcceptsLegalAlphabet() {
        assertAll(
            () -> assertEquals("minecraft:oak_log", ItemId.of("minecraft:oak_log").value()),
            () -> assertEquals("burg:iron-gear", ItemId.of("burg:iron-gear").value()),
            () -> assertEquals("minecraft:blocks/stone", ItemId.of("minecraft:blocks/stone").value()),
            () -> assertEquals("a.b:c", ItemId.of("a.b:c").value())
        );
    }

    @Test
    @DisplayName("ItemId.of(\"\") throws — while parseOrEmpty(\"\") is the EMPTY sentinel")
    void emptyStringStrictVsLenient() {
        assertThrows(IllegalArgumentException.class, () -> ItemId.of(""));
        assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(""),
            "the lenient edge maps the empty string to the sentinel, never throws");
    }

    @Test
    @DisplayName("parseOrEmpty swallows every malformed shape into the sentinel")
    void parseOrEmptyLenient() {
        assertAll(
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(null)),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("garbage")),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(":leading")),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("bad!key")),
            () -> assertEquals(ItemId.of("minecraft:stone"), ItemId.parseOrEmpty("MINECRAFT:STONE"),
                "a good string normalizes through of()")
        );
    }

    @Test
    @DisplayName("EMPTY is minecraft:air and splits cleanly into namespace and path")
    void emptySentinelShape() {
        assertAll(
            () -> assertEquals("minecraft:air", ItemId.EMPTY.value()),
            () -> assertEquals("minecraft", ItemId.EMPTY.namespace()),
            () -> assertEquals("air", ItemId.EMPTY.path())
        );
    }

    @Test
    @DisplayName("namespace() / path() split the canonical form")
    void namespaceAndPathSplit() {
        ItemId oak = ItemId.of("minecraft:oak_log");
        ItemId nested = ItemId.of("burg:tools/hammer");

        assertAll(
            () -> assertEquals("minecraft", oak.namespace()),
            () -> assertEquals("oak_log", oak.path()),
            () -> assertEquals("burg", nested.namespace()),
            () -> assertEquals("tools/hammer", nested.path())
        );
    }

    @Test
    @DisplayName("the raw constructor is lenient: a no-colon value reads as empty namespace, whole value path")
    void rawConstructorLenient() {
        ItemId weird = new ItemId("weird");

        assertAll(
            () -> assertEquals("", weird.namespace(),
                "no colon → no namespace"),
            () -> assertEquals("weird", weird.path(),
                "no colon → the whole value is the path")
        );
    }

    @Test
    @DisplayName("only of() normalizes — raw-constructor values compare case-sensitively")
    void rawConstructorDoesNotNormalize() {
        assertNotEquals(new ItemId("MC:Stone"), ItemId.of("mc:stone"),
            "the raw constructor stores what it is given; of() is the normalizing edge");
    }

    @Test
    @DisplayName("a null value is rejected even by the raw constructor")
    void nullValueRejected() {
        assertThrows(NullPointerException.class, () -> new ItemId(null));
    }
}
