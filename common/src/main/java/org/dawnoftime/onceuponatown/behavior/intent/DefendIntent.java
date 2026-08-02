package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.town.Town;

/**
 * Stub intent for stationing a citizen at the town's defence — manning a wall, patrolling
 * the perimeter, retaliating against a raid.
 *
 * <p>Implemented in Phase BEHAVIOR-5. Returns false from both canResolve and isStillValid
 * so the scheduler will never assign it.
 */
public record DefendIntent(
        ResourceLocation siteId,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        return siteId;
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
