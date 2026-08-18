package org.lowern1ght.burg.application.settlement;

import org.lowern1ght.burg.application.settlement.ports.TownStandingPort;
import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.settlement.StandingBook;
import org.lowern1ght.burg.domain.shared.CitizenId;

/**
 * In-memory fake of {@link TownStandingPort} — the standing book is the real
 * domain object, so use-case tests exercise the same sparse-roll semantics
 * (zero drops off the book) the Town facade relies on. No Minecraft.
 */
final class FakeTownStanding implements TownStandingPort {

    private StandingBook book = StandingBook.empty();
    private Acquisition acquisition = Acquisition.FREE;

    @Override
    public Standing standingFor(CitizenId citizen) {
        return book.standingFor(citizen);
    }

    @Override
    public void adjustStanding(CitizenId citizen, int delta) {
        book = book.adjust(citizen, delta);
    }

    @Override
    public Acquisition acquisition() {
        return acquisition;
    }

    @Override
    public void setAcquisition(Acquisition acquisition) {
        this.acquisition = acquisition;
    }

    int rollSize() {
        return book.size();
    }
}
