package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.town.Town;

/**
 * Stub intent for sending a citizen to a trade job at a particular building.
 *
 * <p>Implemented in Phase BEHAVIOR-2. Returns false from both canResolve and isStillValid
 * so the scheduler will never assign it.
 */
public record TradeIntent(
        ResourceLocation jobId,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        return jobId;
    }

    @Override
    public Town town() {
        return town;
    }

    @Override
    public int basePriority() {
        return priority;
    }

    @Override
    public IntentCost cost() {
        return cost;
    }

    @Override
    public boolean canResolve(Town town) {
        return false;
    }

    @Override
    public boolean isStillValid(Town town) {
        return false;
    }
}
