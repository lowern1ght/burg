package org.lowern1ght.burg.infrastructure.persistence;

import org.lowern1ght.burg.application.settlement.ports.TownStandingPort;
import org.lowern1ght.burg.domain.settlement.Acquisition;
import org.lowern1ght.burg.domain.settlement.Standing;
import org.lowern1ght.burg.domain.shared.CitizenId;
import org.lowern1ght.burg.town.Town;

import java.util.Objects;
import java.util.UUID;

/**
 * Infrastructure adapter: answers {@link TownStandingPort} by delegating to
 * the existing public {@link Town} facade (ADR-0014). Deliberately thin —
 * every method is a one-line delegation plus the {@link CitizenId} →
 * {@link UUID} conversion at the edge; no logic, no caching, no NBT.
 *
 * <p>This is the only class in the standing flow that may import
 * {@code town.Town}. Mutation callers remain responsible for marking the
 * owning {@code LevelTowns} SavedData dirty, exactly as with direct
 * {@code Town} calls.
 */
public final class TownStandingAdapter implements TownStandingPort {

    private final Town town;

    public TownStandingAdapter(Town town) {
        this.town = town;
    }

    @Override
    public Standing standingFor(CitizenId citizen) {
        return town.standingFor(citizen.toUuid());
    }

    @Override
    public void adjustStanding(CitizenId citizen, int delta) {
        town.adjustStanding(citizen.toUuid(), delta);
    }

    @Override
    public Acquisition acquisition() {
        return town.getAcquisition();
    }

    @Override
    public void setAcquisition(Acquisition acquisition) {
        Objects.requireNonNull(acquisition, "acquisition");
        town.setAcquisition(acquisition);
    }
}
