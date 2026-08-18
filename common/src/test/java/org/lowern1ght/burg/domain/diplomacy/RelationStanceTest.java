package org.lowern1ght.burg.domain.diplomacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The five realm-scale relation verbs. The only behavioural edge in the
 * seed is the never-interacted default; the legacy-name mapping
 * (AT_WAR→WAR, ALLY→ALLIANCE) is documented on the enum for the future
 * adapter and deliberately not encoded as a method yet.
 */
class RelationStanceTest {

    @Test
    @DisplayName("exactly the five named stances exist")
    void fiveStances() {
        assertEquals(5, RelationStance.values().length);
    }

    @Test
    @DisplayName("NEUTRAL is the never-interacted default; nothing else is")
    void neutralIsDefault() {
        assertTrue(RelationStance.NEUTRAL.isDefault());
        assertFalse(RelationStance.WAR.isDefault());
        assertFalse(RelationStance.TRUCE.isDefault());
        assertFalse(RelationStance.ALLIANCE.isDefault());
        assertFalse(RelationStance.TRIBUTE.isDefault());
    }
}
