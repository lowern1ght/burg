package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.behavior.role.CitizenRole;
import org.dawnoftime.onceuponatown.town.ItemCost;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.List;
import java.util.Set;

/**
 * An intent to construct a new building of the given def in the town.
 *
 * <p>The most common intent. Once {@link #canResolve} returns true the scheduler pairs this
 * intent with a free builder citizen and the citizen's task queue carries a
 * {@link org.dawnoftime.onceuponatown.behavior.task.BuildTask}.
 *
 * <p>{@link #isStillValid} keeps the intent simple: the building is no longer needed if it
 * already stands in the town. That is the only "supersedes" check — a richer rule (e.g.
 * "drop if a higher-priority build intent for the same defId exists") can be layered in
 * later without changing the interface.
 */
public record BuildIntent(
        ResourceLocation buildingDefId,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    @Override
    public ResourceLocation id() {
        return buildingDefId;
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
    public Set<CitizenRole> requiredRoles() {
        return Set.of(CitizenRole.BUILDER);
    }

    @Override
    public boolean canResolve(Town town) {
        if (town.getBuilderNpcIds().isEmpty()) {
            return false;
        }
        return cost.isEmpty() || town.getTownInventory().hasStock(toItemCost(cost));
    }

    @Override
    public boolean isStillValid(Town town) {
        String id = buildingDefId.toString();
        return town.getBuildings().stream().noneMatch(b -> b.getDefId().equals(id));
    }

    private static List<ItemCost> toItemCost(IntentCost cost) {
        return cost.entries().stream()
            .map(e -> new ItemCost(BuiltInRegistries.ITEM.get(e.itemId()), e.amount()))
            .toList();
    }
}
