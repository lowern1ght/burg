package org.dawnoftime.onceuponatown.building.type;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;

import java.util.List;

public class BuildingType extends BuildType {
    private final Item iconItem;

    public BuildingType(String buildingTypeId, int weight, List<BuildLevel> levels, BuildingPurpose purpose, Item iconItem, List<BuildVariant> variants, String cultureId) {
        super(buildingTypeId, weight, levels, purpose);
        this.iconItem = iconItem;
        variants.forEach((variant) -> addVariant(variant, null, cultureId));
    }

    public BuildVariant getRandomVariant(RandomSource rand) {
        BuildVariant[] variants = this.getBuildVariants().values().toArray(new BuildVariant[0]);
        return variants[rand.nextInt(variants.length)];
    }

    public Item getIconItem() {
        return iconItem;
    }
}
