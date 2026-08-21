package org.lowern1ght.burg.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.town.Town;

import java.util.UUID;

/**
 * Stub intent for calling a citizen back from wherever they went — combat, a trade run, an
 * errand. Implemented in Phase BEHAVIOR-5.
 *
 * @param citizenId the citizen being recalled; never null
 * @param town the town issuing the recall; never null
 * @param priority base scheduling priority for this intent
 * @param cost resource cost for the recall (typically empty); never null
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
            "burg", "recall/" + citizenId);
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
