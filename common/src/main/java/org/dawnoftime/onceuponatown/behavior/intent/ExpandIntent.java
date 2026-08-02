package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.town.Town;

/**
 * Stub intent for laying roads and connecting the town to the wider world.
 *
 * <p>Implemented in Phase BEHAVIOR-3. Until then the record compiles, the interface contract
 * is satisfied, and the no-op booleans make it impossible for the scheduler to assign it to
 * a citizen by mistake.
 */
public record ExpandIntent(
        ResourceLocation targetDefId,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        return targetDefId;
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
