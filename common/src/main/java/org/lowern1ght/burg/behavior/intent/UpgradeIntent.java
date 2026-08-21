package org.lowern1ght.burg.behavior.intent;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.lowern1ght.burg.behavior.role.CitizenRole;
import org.lowern1ght.burg.town.BuildingDef;
import org.lowern1ght.burg.town.Town;

import java.util.Set;
import java.util.UUID;

/**
 * An intent to upgrade a placed building to its next level.
 *
 * @param buildingDefId the building definition id; never null
 * @param buildingPos world position of the placed building; never null
 * @param town the town that owns the building; never null
 * @param priority base scheduling priority for this intent
 * @param cost resource cost to begin the upgrade; never null (empty allowed)
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
        // DIST-2 era gate: upgrading a building requires the town's current era
        // to be strictly greater than the era the building was unlocked at
        // (the +1 means "advance one era past unlock before upgrades become
        // available"). The first slice uses a no-op eraFor (returns 0), so the
        // gate becomes "currentEra >= 1" — upgrades blocked at era 0, allowed
        // at era 1+ once the eraFor stub is replaced with the real field read.
        int unlockEra = eraFor(buildingDefId);
        if (town.getCurrentEra() < unlockEra + 1) {
            return false;
        }
        return town.getBuildings().stream()
            .filter(b -> b.getDefId().equals(id) && b.worldPos.equals(buildingPos))
            .findFirst()
            .map(b -> {
                BuildingDef def = org.lowern1ght.burg.datapack.BuildingDataHandler.get(id).orElse(null);
                if (def == null) return false;
                int maxLevel = Math.max(def.upgrades.size(), def.nbtLevels.size());
                return b.getUpgradeLevel() < maxLevel;
            })
            .orElse(false);
    }

    /**
     * Era the building def was unlocked at. Returns 0 for the first slice
     * (every building is unlocked at era 0) because the era field is not yet
     * exposed on BuildingDef / BuildingDataHandler. A future commit adds the
     * field, this method reads it, and the era gate in {@link #canResolve}
     * becomes fully data-driven.
     */
    private static int eraFor(ResourceLocation defId) {
        return 0;
    }

    @Override
    public boolean isStillValid(Town town) {
        String id = buildingDefId.toString();
        return town.getBuildings().stream()
            .anyMatch(b -> b.getDefId().equals(id) && b.worldPos.equals(buildingPos));
    }
}
