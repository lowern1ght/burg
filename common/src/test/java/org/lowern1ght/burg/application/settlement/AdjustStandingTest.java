package org.lowern1ght.burg.application.settlement;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lowern1ght.burg.application.settlement.ports.TownStandingPort;
import org.lowern1ght.burg.domain.settlement.Acquisition;
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

    @Test
    @DisplayName("adjusting one citizen leaves every other citizen's score alone")
    void adjustmentsAreScopedToTheCitizen() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        handler.handle(new AdjustStanding(id(1), 7));
        handler.handle(new AdjustStanding(id(2), 3));

        assertAll(
            () -> assertEquals(7, handler.handle(new AdjustStanding(id(1), 0)).value(),
                "citizen 1 is read back at the accumulated score"),
            () -> assertEquals(3, handler.handle(new AdjustStanding(id(2), 0)).value(),
                "citizen 2 is read back at its own score"),
            () -> assertEquals(2, town.rollSize(),
                "the book carries two distinct entries — one per citizen")
        );
    }

    @Test
    @DisplayName("a maximum-magnitude delta is accepted at the command boundary")
    void maxDeltaAccepted() {
        FakeTownStanding town = new FakeTownStanding();
        AdjustStanding.Handler handler = new AdjustStanding.Handler(town);

        // Pin the upper-bound contract: Integer.MAX_VALUE is a valid delta
        // and the handler carries it through. The Standing score is
        // int-keyed — a second MAX_VALUE adjustment would wrap, which is
        // the model's behaviour, not the boundary's.
        Standing first = handler.handle(new AdjustStanding(id(1), Integer.MAX_VALUE));

        assertAll(
            () -> assertEquals(Integer.MAX_VALUE, first.value(),
                "a single MAX_VALUE delta reads back exactly as adjusted"),
            () -> assertEquals(1, town.rollSize(),
                "the citizen is on the roll — the score is non-zero")
        );
    }

    @Test
    @DisplayName("the handler rethrows a port failure without leaving partial state")
    void portFailurePropagates() {
        TownStandingPort brokenPort = new TownStandingPort() {
            @Override
            public Standing standingFor(CitizenId citizen) {
                throw new IllegalStateException("port dead");
            }

            @Override
            public void adjustStanding(CitizenId citizen, int delta) {
                throw new IllegalStateException("port dead");
            }

            @Override
            public Acquisition acquisition() {
                throw new IllegalStateException("port dead");
            }

            @Override
            public void setAcquisition(Acquisition acquisition) {
                throw new IllegalStateException("port dead");
            }
        };
        AdjustStanding.Handler handler = new AdjustStanding.Handler(brokenPort);

        assertThrows(IllegalStateException.class,
            () -> handler.handle(new AdjustStanding(id(1), 5)),
            "a failing port surfaces its exception — the use case never swallows it");
    }
}
