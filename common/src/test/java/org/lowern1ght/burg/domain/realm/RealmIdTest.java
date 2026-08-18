package org.lowern1ght.burg.domain.realm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Realm identity wrapper, in pure JUnit. Bare JVM, no Minecraft —
 * the strict/blank boundary is exactly the kind of detail a facade
 * edge will lean on once the LevelRealms storage question is settled.
 */
class RealmIdTest {

    @Test
    @DisplayName("of trims surrounding whitespace into the canonical form")
    void ofTrims() {
        assertEquals(new RealmId("mercia"), RealmId.of("  mercia  "));
    }

    @Test
    @DisplayName("value equality follows the canonical string")
    void valueEquality() {
        assertEquals(RealmId.of("mercia"), RealmId.of("mercia"));
        assertNotEquals(RealmId.of("mercia"), RealmId.of("northumbria"));
    }

    @Test
    @DisplayName("of rejects null and blank strings")
    void ofRejectsBlank() {
        assertAllOfThrows(null, "", "   ", "\t");
    }

    @Test
    @DisplayName("the canonical string survives the round trip")
    void roundTrip() {
        var id = RealmId.of("wessex");
        assertEquals("wessex", id.value());
        assertEquals(id, RealmId.of(id.value()));
        assertTrue(id.value().indexOf(' ') < 0, "no whitespace survives the factory");
    }

    private static void assertAllOfThrows(String... raws) {
        for (String raw : raws) {
            assertThrows(Exception.class, () -> RealmId.of(raw),
                "blank input '" + raw + "' must be rejected");
        }
    }
}
