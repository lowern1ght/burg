package org.dawnoftime.onceuponatown.town;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;

public class BuildingDef {
    public final String id;
    public final ResourceLocation nbt;
    // Pool name of this building's entry jigsaw. Must match the targetPool of the connection point.
    // Empty string means this building can be placed at any connection regardless of pool.
    public final String entryPool;
    public final List<ProductionEntry> production;
    public final List<ItemCost> constructionCost;
    // When true, placement snaps Y to ground level (for streets that must follow terrain).
    public final boolean terrainMatching;
    // Item resource location shown as icon in the map tooltip, e.g. "minecraft:yellow_bed".
    public final String iconItem;
    // Logical group for map display: "buildings" or "gardens". Roads are identified by terrainMatching.
    public final String category;
    // XZ footprint for map rendering: list of strings, one per Z row, each char is an X column.
    // '1' = road cell rendered on map, '0' = gap. Null means render bounding box as solid rectangle.
    public final List<String> footprint;
    // Ordered list of transformation recipes. Empty = not a transformer.
    public final List<TransformationRecipe> transformations;
    // Fraction of town stock taken as budget per input item at the start of each transform pass.
    public final float transformInputRatio;
    // Ticks between each transformation pass.
    public final int transformEveryTicks;
    // Village orientation this starter building provides ("food", "wood", "stone"). Empty for non-starters.
    public final String orientation;
    // Ordered list of building IDs to place during bootstrap. All entries are placed (no random pick). Empty for non-starters.
    public final List<String> bootstrapBuildings;
    // Additive bonus applied to village-wide production amounts (e.g. 0.03 = +3% per building).
    public final double productionBonus;
    // Number of resident slots this building adds to the village.
    public final int residents;
    // Ordered upgrade levels. Entry [0] = level 1, [1] = level 2, etc. Empty = not upgradable.
    public final List<UpgradeLevel> upgrades;
    // NBT files for each visual upgrade tier. Entry [0] = visual for level 1, [1] = visual for level 2, etc.
    // If absent for a given level, the upgrade is stat-only (no visual change).
    public final List<ResourceLocation> nbtLevels;

    // One upgrade step: cost + what it changes. All fields are additive deltas.
    public record UpgradeLevel(float cadenceMultiplier, int capacityStacksAdd, int amountAdd, List<ItemCost> upgradeCost) {}

    // Effective stats for a building at a given upgrade level (cached by PlacedBuilding).
    public record ResolvedBuildingStats(List<ProductionEntry> production, double totalCadenceMultiplier) {}

    public BuildingDef(String id, ResourceLocation nbt, String entryPool,
                       List<ProductionEntry> production, List<ItemCost> constructionCost,
                       boolean terrainMatching, String iconItem, String category,
                       List<String> footprint,
                       List<TransformationRecipe> transformations,
                       float transformInputRatio, int transformEveryTicks,
                       String orientation, List<String> bootstrapBuildings,
                       double productionBonus, int residents,
                       List<UpgradeLevel> upgrades, List<ResourceLocation> nbtLevels) {
        this.id = id;
        this.nbt = nbt;
        this.entryPool = entryPool;
        this.production = production;
        this.constructionCost = constructionCost;
        this.terrainMatching = terrainMatching;
        this.iconItem = iconItem;
        this.category = category;
        this.footprint = footprint;
        this.transformations = transformations;
        this.transformInputRatio = transformInputRatio;
        this.transformEveryTicks = transformEveryTicks;
        this.orientation = orientation;
        this.bootstrapBuildings = bootstrapBuildings;
        this.productionBonus = productionBonus;
        this.residents = residents;
        this.upgrades = upgrades;
        this.nbtLevels = nbtLevels;
    }

    // Returns effective production and cadence multiplier for the given upgrade level.
    // Level 0 = base stats with no upgrades applied.
    public ResolvedBuildingStats resolveAtLevel(int level) {
        if (level <= 0 || upgrades.isEmpty()) {
            return new ResolvedBuildingStats(production, 0.0);
        }
        int capped = Math.min(level, upgrades.size());
        double totalCadence = 0.0;
        int totalCapAdd = 0;
        int totalAmountAdd = 0;
        for (int i = 0; i < capped; i++) {
            totalCadence    += upgrades.get(i).cadenceMultiplier();
            totalCapAdd     += upgrades.get(i).capacityStacksAdd();
            totalAmountAdd  += upgrades.get(i).amountAdd();
        }
        if (totalCapAdd == 0 && totalAmountAdd == 0) {
            return new ResolvedBuildingStats(production, totalCadence);
        }
        int finalCapAdd    = totalCapAdd;
        int finalAmountAdd = totalAmountAdd;
        List<ProductionEntry> adjusted = production.stream()
            .map(e -> new ProductionEntry(
                e.item(),
                e.amount() + finalAmountAdd,
                e.everyTicks(),
                e.capacityStacks() + finalCapAdd))
            .toList();
        return new ResolvedBuildingStats(adjusted, totalCadence);
    }

    public boolean isTransformer() { return !transformations.isEmpty(); }
}
