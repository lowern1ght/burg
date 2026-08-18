package org.lowern1ght.burg.application.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.shared.CitizenId;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The {@link AdjustStanding} use case against an in-memory fake port —
 * pure JUnit, no Minecraft (ADR-0014). The fake holds a real
 * {@code StandingBook}, so these tests also pin the sparse-roll discipline
 * the Town-side adapter inherits for free.
 */
class AdjustStandingTest {

    private static CitizenId id(int seed) {
        // Deterministic UUIDs so the assertions stay legible.
        return CitizenId.of(new UUID(0x1234L, seed));
    }

    @Test
    @DisplayName("adjust accumulates through the port and returns the new standing")
    void adjustAccumulates() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        Standing first = handler.handle(new AdjustStanding(id(1), 5));
        Standing second = handler.handle(new AdjustStanding(id(1), 7));

        assertAll(
            () -> assertEquals(5, first.value(), "first handle returns the post-adjustment read"),
            () -> assertEquals(12, second.value(), "second handle accumulates"),
            () -> assertEquals(id(1), second.citizen(), "the returned standing names the citizen"),
            () -> assertEquals(12, town.standingFor(id(1)).value(),
                "the port itself sees the accumulated score")
        );
    }

    @Test
    @DisplayName("a negative delta decreases the score")
    void negativeDeltaDecreases() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        handler.handle(new AdjustStanding(id(1), 10));

        assertEquals(4, handler.handle(new AdjustStanding(id(1), -6)).value());
    }

    @Test
    @DisplayName("adjusting back to zero drops the citizen off the roll")
    void adjustToZeroDropsEntry() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        handler.handle(new AdjustStanding(id(1), 10));
        Standing cleared = handler.handle(new AdjustStanding(id(1), -10));

        assertAll(
            () -> assertEquals(Standing.DEFAULT, cleared.value(),
                "the read is zero, never absent"),
            () -> assertEquals(0, town.rollSize(),
                "the sparse roll drops the zero entry"),
            () -> assertEquals(Standing.DEFAULT, town.standingFor(id(1)).value())
        );
    }

    @Test
    @DisplayName("adjusting a citizen never on the roll starts them at zero plus delta")
    void freshCitizenStartsAtZero() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        assertEquals(3, handler.handle(new AdjustStanding(id(7), 3)).value());
    }

    @Test
    @DisplayName("a null citizen fails fast at the command boundary")
    void nullCitizenRejected() {
        assertThrows(NullPointerException.class,
            () -> new AdjustStanding(null, 5));
    }
}
