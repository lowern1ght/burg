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
    // Ordered list of building IDs to place at zero cost during bootstrap. Empty for non-starters.
    public final List<String> bootstrapCandidates;
    // Additive bonus applied to village-wide production amounts (e.g. 0.03 = +3% per building).
    public final double productionBonus;

    public BuildingDef(String id, ResourceLocation nbt, String entryPool,
                       List<ProductionEntry> production, List<ItemCost> constructionCost,
                       boolean terrainMatching, String iconItem, String category,
                       List<String> footprint,
                       List<TransformationRecipe> transformations,
                       float transformInputRatio, int transformEveryTicks,
                       String orientation, List<String> bootstrapCandidates,
                       double productionBonus) {
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
        this.bootstrapCandidates = bootstrapCandidates;
        this.productionBonus = productionBonus;
    }

    public boolean isTransformer() { return !transformations.isEmpty(); }
}
