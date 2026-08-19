package org.lowern1ght.burg.domain.diplomacy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mutation-style invariants for {@link RelationStance}: NEUTRAL is the
 * only default, and the five verbs are a closed set.
 */
class RelationStanceMutationTest {

    @Test
    @DisplayName("only NEUTRAL isDefault — every other stance knows it is an interaction")
    void onlyNeutralIsDefault() {
        for (RelationStance stance : RelationStance.values()) {
            assertEquals(stance == RelationStance.NEUTRAL, stance.isDefault(),
                "isDefault must hold for NEUTRAL alone (got " + stance + ")");
        }
    }

    @Test
    @DisplayName("war and truce are distinct verbs, neither is the default")
    void warAndTruceAreNotDefaults() {
        assertAll(
            () -> assertFalse(RelationStance.WAR.isDefault()),
            () -> assertFalse(RelationStance.TRUCE.isDefault()),
            () -> assertTrue(RelationStance.NEUTRAL.isDefault())
        );
    }

    @Test
    @DisplayName("exactly five stances exist — the relation vocabulary is closed")
    void exactlyFiveStances() {
        assertEquals(5, RelationStance.values().length,
            "WAR, TRUCE, ALLIANCE, TRIBUTE, NEUTRAL");
    }

    @Test
    @DisplayName("valueOf round-trips each name — the persisted layer is stable")
    void valueOfRoundTrip() {
        for (RelationStance stance : RelationStance.values()) {
            assertEquals(stance, RelationStance.valueOf(stance.name()));
        }
    }
}
