package org.lowern1ght.burg.behavior.intent;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.town.ItemCost;
import org.lowern1ght.burg.town.Town;

import java.util.List;
import java.util.Set;

/**
 * An intent to construct a new building of the given def in the town.
 *
 * <p>The most common intent. Once {@link #canResolve} returns true the scheduler pairs this
 * intent with a free builder citizen and the citizen's task queue carries a
 * {@link org.lowern1ght.burg.behavior.task.BuildTask}.
 *
 * <p>{@link #isStillValid} keeps the intent simple: the building is no longer needed if it
 * already stands in the town. That is the only "supersedes" check — a richer rule (e.g.
 * "drop if a higher-priority build intent for the same defId exists") can be layered in
 * later without changing the interface.
 *
 * <p><b>DIST-1 zoning:</b> {@link #requiredZone} declares the zone the building wants
 * to live in. The first slice carries the field and verifies it via {@link #canResolve};
 * the actual position-picking enforcement (Town.tryAddToConstructionQueue picking a slot
 * inside the zone rather than anywhere) is deferred to a follow-up commit.
 *
 * <p><b>DIST-2 era gate:</b> {@link #canResolve} also checks the town's current era
 * against the era the building is unlocked at. The first slice uses a no-op
 * {@link #eraFor} (returns 0 for every defId) so the gate is wired but inert until
 * BuildingDataHandler exposes the era field.
 */
public record BuildIntent(
        ResourceLocation buildingDefId,
        Town town,
        int priority,
        IntentCost cost,
        Town.Zone requiredZone
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
        if (!cost.isEmpty() && !town.getTownInventory().hasStock(toItemCost(cost))) {
            return false;
        }

        // DIST-2 era gate: building must be unlocked at the town's current era.
        // Disabled until BuildingDataHandler exposes the era field — see eraFor().
        int requiredEra = eraFor(buildingDefId);
        if (town.getCurrentEra() < requiredEra) {
            return false;
        }

        // DIST-1 zoning: requiredZone is carried on the intent; full enforcement
        // (Town.tryAddToConstructionQueue picking a position inside the zone)
        // is deferred to a follow-up commit. The gate here is wired but inert.
        if (requiredZone == null) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isStillValid(Town town) {
        String id = buildingDefId.toString();
        return town.getBuildings().stream().noneMatch(b -> b.getDefId().equals(id));
    }

    /**
     * The era a building def is unlocked at. Returns 0 for the first slice
     * (every building is always available) because the era field is not yet
     * exposed on BuildingDef / BuildingDataHandler. A future commit adds the
     * field, this method reads it, and the era gate in {@link #canResolve}
     * becomes effective.
     */
    private static int eraFor(ResourceLocation defId) {
        return 0;
    }

    private static List<ItemCost> toItemCost(IntentCost cost) {
        return cost.entries().stream()
            .map(e -> new ItemCost(BuiltInRegistries.ITEM.get(e.itemId()), e.amount()))
            .toList();
    }
}
