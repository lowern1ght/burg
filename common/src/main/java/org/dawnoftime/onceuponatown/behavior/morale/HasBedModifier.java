package org.dawnoftime.onceuponatown.behavior.morale;

import net.minecraft.server.level.ServerLevel;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.UUID;

/**
 * A citizen with a bed is happier than one sleeping rough.
 *
 * <p>The first-slice rule is a flat +3; the negative case (no bed, a
 * smaller penalty) is reserved for a future slice once the per-citizen
 * housing accessor lands. Until then this is an optimistic default.
 */
public final class HasBedModifier implements MoodModifier {

    /** Optimistic default: assume every citizen has a bed. */
    private static final int DELTA = 3;

    @Override
    public int modify(UUID citizenId, Town town, int currentMorale, ServerLevel level) {
        return currentMorale + DELTA;
    }

    @Override
    public String name() {
        return "HasBed";
    }
}
