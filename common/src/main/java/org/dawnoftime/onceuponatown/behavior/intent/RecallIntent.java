package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.UUID;

/**
 * Stub intent for calling a citizen back from wherever they went — combat, a trade run, an
 * errand. Implemented in Phase BEHAVIOR-5.
 */
public record RecallIntent(
        UUID citizenId,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
            "onceuponatown", "recall/" + citizenId);
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
