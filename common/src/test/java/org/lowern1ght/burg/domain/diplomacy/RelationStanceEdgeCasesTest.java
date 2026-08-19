package org.lowern1ght.burg.domain.diplomacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tester edge cases for {@link RelationStance}. The stance names are the
 * future wire format (a realm-relations save / sync packet will persist
 * them), so the enum's name stability and default are pinned here.
 */
class RelationStanceEdgeCasesTest {

    @Test
    @DisplayName("every stance round-trips through its name — the wire form is stable")
    void nameRoundTrip() {
        for (RelationStance stance : RelationStance.values()) {
            assertEquals(stance, RelationStance.valueOf(stance.name()),
                stance.name() + " survives a name round-trip");
        }
    }

    @Test
    @DisplayName("exactly one default exists — NEUTRAL, and it is not WAR")
    void exactlyOneDefault() {
        int defaults = 0;
        for (RelationStance stance : RelationStance.values()) {
            if (stance.isDefault()) defaults++;
        }
        assertEquals(1, defaults, "exactly one stance is the default");
        assertTrue(RelationStance.NEUTRAL.isDefault());
    }

    @Test
    @DisplayName("the five verbs are distinct — no aliasing in equals or name")
    void noAliasing() {
        RelationStance[] stances = RelationStance.values();
        for (int i = 0; i < stances.length; i++) {
            for (int j = i + 1; j < stances.length; j++) {
                assertFalse(stances[i].name().equals(stances[j].name()),
                    stances[i] + " and " + stances[j] + " must have distinct names");
            }
        }
        assertAll(
            () -> assertEquals(5, stances.length),
            () -> assertFalse(RelationStance.WAR.isDefault(),
                "WAR is deliberately not the default — two never-met realms are NEUTRAL")
        );
    }
}
