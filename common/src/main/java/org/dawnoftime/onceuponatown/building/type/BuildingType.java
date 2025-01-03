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
import org.dawnoftime.onceuponatown.culture.CultureFileHelper;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.*;

public class BuildingType extends BuildType {
    private final Item iconItem;

    private BuildingType(String buildingTypeId, int weight, List<BuildLevel> levels, BuildingPurpose purpose, Item iconItem, List<BuildVariant> variants, String cultureId) {
        super(buildingTypeId, weight, levels, purpose);
        this.iconItem = iconItem;
        variants.forEach((variant) -> addVariant(variant, null, cultureId));
    }

    public static @NotNull BuildingType createFromDataPack(String cultureId, ResourceLocation buildingRl, ResourceManager resourceManager) {
        String path = buildingRl.getPath();
        String buildingTypeId = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        CultureFileHelper helper = new CultureFileHelper(cultureId, buildingTypeId + ".json", buildingRl, "building type");
        try (Reader reader = resourceManager.getResource(buildingRl).orElseThrow().openAsReader()) {
            JsonObject rootJson = GsonHelper.parse(reader);
            /* Reading data shared by all types (roads, buildings...) : id, weight, levels... */
            BuildTypeCommonJsonData commonData = readJsonCommonData(cultureId, buildingTypeId, rootJson, helper, resourceManager);
            /* Reading building purpose */
            String purposeId = helper.getString(rootJson, "category").toUpperCase();
            BuildingPurpose purpose = null;
            try {
                purpose = BuildingPurpose.valueOf(purposeId);
            } catch (Exception e) {
                helper.throwInvalidField("category", "Unknown building category.");
            }
            /* Reading icon item */
            Item iconItem = helper.getItem(rootJson, "item");
            /* Removing variant shape since it's not used by buildings */
            List<BuildVariant> variants = new ArrayList<>();
            commonData.variants().forEach((id, pair) -> variants.add(pair.getA()));
            /* Finished reading building type */
            return new BuildingType(buildingTypeId, commonData.weight(), commonData.levels(), purpose, iconItem, variants, cultureId);
        } catch (NoSuchElementException | IOException | JsonParseException e) {
            throw new CorruptedCultureException(cultureId, "Could not read building type file '" + buildingTypeId + "'.json, supposed to be located at " + Utils.serverRlToDebug(buildingRl) + ". " + e.getMessage());
        }
    }

    public BuildVariant getRandomVariant(RandomSource rand) {
        BuildVariant[] variants = this.getBuildVariants().values().toArray(new BuildVariant[0]);
        return variants[rand.nextInt(variants.length)];
    }

    public Item getIconItem() {
        return iconItem;
    }
}
