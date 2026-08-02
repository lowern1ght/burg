package org.dawnoftime.onceuponatown.behavior.intent;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.behavior.role.CitizenRole;
import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.Town;

import java.util.Set;
import java.util.UUID;

/**
 * An intent to upgrade a placed building to its next level.
 *
 * <p>The target is identified by the building's def id and its world position. {@link
 * #canResolve} verifies that the building exists and has another level to reach; {@link
 * #isStillValid} returns false if the building is gone (demolished, the town lost its
 * foothold).
 */
public record UpgradeIntent(
        ResourceLocation buildingDefId,
        BlockPos buildingPos,
        Town town,
        int priority,
        IntentCost cost
) implements TownIntent {

    private static String key(UpgradeIntent i) {
        return i.buildingDefId.toString() + "@" + i.buildingPos.asLong();
    }

    @Override
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(
            buildingDefId.getNamespace(),
            "upgrade/" + buildingDefId.getPath() + "/" + UUID.randomUUID());
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
        if (town.getBuilderNpcIds().size() >= town.getTargetBuilderCount()) {
            return false;
        }
        String id = buildingDefId.toString();
        return town.getBuildings().stream()
            .filter(b -> b.getDefId().equals(id) && b.worldPos.equals(buildingPos))
            .findFirst()
            .map(b -> {
                BuildingDef def = org.dawnoftime.onceuponatown.datapack.BuildingDataHandler.get(id).orElse(null);
                if (def == null) return false;
                int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
                return b.getUpgradeLevel() < maxLevel;
            })
            .orElse(false);
    }

    @Override
    public boolean isStillValid(Town town) {
        String id = buildingDefId.toString();
        return town.getBuildings().stream()
            .anyMatch(b -> b.getDefId().equals(id) && b.worldPos.equals(buildingPos));
    }
}
