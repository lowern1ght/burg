package org.dawnoftime.onceuponatown.datapack;

import org.dawnoftime.onceuponatown.town.BuildingDef;
import org.dawnoftime.onceuponatown.town.ItemCost;

import java.util.List;

public class EraTransitionDef {
    public final String id;
    public final int fromEra;
    // Empty = available from any orientation
    public final String fromOrientation;
    public final String displayName;
    public final String iconItem;
    public final int minWeight;
    public final List<ItemCost> resourceCost;
    public final int requiredResidents;
    public final List<BuildingDef.BuildingRequirement> requiredBuildings;
    // Building defIds added to Town.unlockedBuildingIds when this transition completes
    public final List<String> unlockedBuildingIds;
    // Orientation tag stored in Town.currentOrientation after the transition
    public final String nextOrientation;

    public EraTransitionDef(String id, int fromEra, String fromOrientation, String displayName,
                            String iconItem, int minWeight, List<ItemCost> resourceCost,
                            int requiredResidents, List<BuildingDef.BuildingRequirement> requiredBuildings,
                            List<String> unlockedBuildingIds, String nextOrientation) {
        this.id = id;
        this.fromEra = fromEra;
        this.fromOrientation = fromOrientation;
        this.displayName = displayName;
        this.iconItem = iconItem;
        this.minWeight = minWeight;
        this.resourceCost = resourceCost;
        this.requiredResidents = requiredResidents;
        this.requiredBuildings = requiredBuildings;
        this.unlockedBuildingIds = unlockedBuildingIds;
        this.nextOrientation = nextOrientation;
    }
}
