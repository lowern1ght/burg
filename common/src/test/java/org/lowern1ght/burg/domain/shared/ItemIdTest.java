package org.lowern1ght.burg.domain.shared;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Canonical-form discipline of {@link ItemId}. The record wraps the
 * Minecraft {@code ResourceLocation} string form ({@code "namespace:path"})
 * and never stores an alternate form — that is the contract the Town
 * facade's NBT serializer and the future StockLedger domain view both rely
 * on.
 *
 * <p>Like {@link CitizenIdTest}, the tests are pure JUnit on a bare JVM:
 * no Minecraft on the classpath. This is the second value object in the
 * domain layer that proves the Minecraft-free discipline.
 */
class ItemIdTest {

    @Test
    @DisplayName("the canonical form is lowercase 'namespace:path'")
    void canonicalForm() {
        ItemId id = ItemId.of("Minecraft:Stone");

        assertAll(
            () -> assertEquals("minecraft:stone", id.value(),
                "the wrapped form is lowercased"),
            () -> assertEquals("minecraft", id.namespace(),
                "namespace() returns the part before the colon"),
            () -> assertEquals("stone", id.path(),
                "path() returns the part after the colon")
        );
    }

    @Test
    @DisplayName("two ItemIds wrapping the same canonical string are equal")
    void equality() {
        assertEquals(ItemId.of("burg:oak_log"), ItemId.of("BURG:OAK_LOG"));
    }

    @Test
    @DisplayName("different values are unequal")
    void inequality() {
        assertNotEquals(ItemId.of("minecraft:stone"), ItemId.of("minecraft:dirt"));
    }

    @Test
    @DisplayName("parseOrEmpty is lenient — bad strings become EMPTY")
    void parsePolicy() {
        assertAll(
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(null),
                "null reads as EMPTY"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(""),
                "empty string reads as EMPTY"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("stone"),
                "missing colon reads as EMPTY"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty(":stone"),
                "empty namespace reads as EMPTY"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("minecraft:"),
                "empty path reads as EMPTY"),
            () -> assertSame(ItemId.EMPTY, ItemId.parseOrEmpty("not a valid:one"),
                "spaces in namespace read as EMPTY"),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ItemId.of("not a valid:one"),
                "of() is strict — the boundary caller must catch its own garbage")
        );
    }

    @Test
    @DisplayName("the path segment may contain slashes, dots, underscores and dashes")
    void pathSegments() {
        assertAll(
            () -> assertEquals("logs/oak", ItemId.of("minecraft:logs/oak").path()),
            () -> assertEquals("cobblestone_wall", ItemId.of("burg:cobblestone_wall").path()),
            () -> assertEquals("piggy-bank", ItemId.of("burg:piggy-bank").path()),
            () -> assertEquals("v1.0.0", ItemId.of("burg:v1.0.0").path())
        );
    }
}