package org.dawnoftime.onceuponatown.behavior.intent;

import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Callback the behaviour engine uses to look up citizens without taking a hard dependency on
 * the entity layer.
 *
 * <p>The scheduler keeps no reference to any specific citizen at construction time. The
 * real wiring happens in the composition root (the loader module), which knows how to walk
 * the loaded entities and translate between UUIDs and live instances. Tests provide a stub
 * that returns a fixed list — see {@code IntentSchedulerTest}.
 */
public interface NpcSupplier {

    /** Citizens in this town who have no task assigned to them right now. */
    List<Npc> freeCitizens(Town town);

    /** The citizen with this UUID, or empty if not currently in the world. */
    Optional<Npc> findByUuid(UUID id);
}
