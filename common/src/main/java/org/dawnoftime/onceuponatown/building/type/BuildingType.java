package org.dawnoftime.onceuponatown.building.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

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
