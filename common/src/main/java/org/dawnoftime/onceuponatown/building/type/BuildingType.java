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
            throw new CorruptedCultureException(cultureId, "Could not read building type file '" + buildingTypeId + "'.json, supposed to be located at " + Utils.rlToDebug(buildingRl) + ". " + e.getMessage());
        }
    }

    /*
    public static @NotNull BuildingType createFromDataPack(String cultureId, ResourceLocation buildingRl, ResourceManager resourceManager) {
        String path = buildingRl.getPath();
        String buildingTypeId = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        CultureFileHelper helper = new CultureFileHelper(cultureId, buildingTypeId + ".json", buildingRl, "building type");
        try (Reader reader = resourceManager.getResource(buildingRl).orElseThrow().openAsReader()) {
            JsonObject rootJson = GsonHelper.parse(reader);
            // Reading mandatory id. It should match the folder name of the Json file.
            if (!helper.getString(rootJson, "id").equals(buildingTypeId)) {
                helper.throwInvalidField("id", "It should match the name of the building type json file." );
            }
            // Reading building purpose
            String purposeId = helper.getString(rootJson, "category").toUpperCase();
            BuildingPurpose purpose = null;
            try {
                purpose = BuildingPurpose.valueOf(purposeId);
            } catch (Exception e) {
                helper.throwInvalidField("category", "Unknown building category.");
            }
            // Reading icon item
            Item iconItem = helper.getItem(rootJson, "item");
            // Reading weight
            int weight = helper.getInt(rootJson, "weight");
            if (weight < 0) {
                helper.throwInvalidField("weight", "It must be >= 0.");
            }
            // Reading build levels
            List<BuildLevel> levels = new ArrayList<>();
            JsonArray levelsArray = helper.getJsonArray(rootJson, "levels");
            String loc = "in levels[]";
            int level = 1;
            for (JsonElement element : levelsArray) {
                JsonObject elemJson = helper.asJsonObject(element, "levels[] element", loc);
                int requiredEra = helper.getInt(elemJson, "required_era", loc);
                if (requiredEra < 0) {
                    helper.throwInvalidField("required_era", loc, "It must be >= 0.");
                }
                int experienceGain = helper.getInt(elemJson, "experience_gain", loc);
                if (experienceGain < 0) {
                    helper.throwInvalidField("experience_gain", loc, "It must be >= 0.");
                }
                int dwellingSlots = helper.getInt(elemJson, "dwelling_slots", loc);
                if (dwellingSlots < 0) {
                    helper.throwInvalidField("dwelling_slots", loc, "It must be >= 0.");
                }
                // TODO read working slots
                levels.add(new BuildLevel(level, requiredEra, experienceGain, new HashMap<>(), dwellingSlots));
                ++level;
            }
            // Reading building variants
            HashMap<String, BuildVariant> variants = createVariants(cultureId, rootJson, helper, resourceManager);
            // Finished reading building type
            return new BuildingType(buildingTypeId, weight, levels, purpose, iconItem, variants, cultureId);
        } catch (NoSuchElementException | IOException | JsonParseException e) {
            throw new CorruptedCultureException(cultureId, "Could not read building type file '" + buildingTypeId + "'.json, supposed to be located at " + Utils.rlToDebug(buildingRl) + ". " + e.getMessage());
        }
    }

    private static HashMap<String, BuildVariant> createVariants(String cultureId, JsonObject rootJson, CultureFileHelper helper, ResourceManager resourceManager) {
        HashMap<String, BuildVariant> variants = new HashMap<>();
        JsonArray variantsArray = helper.getJsonArray(rootJson, "variants");
        String loc = "in variants[]";
        for (JsonElement element : variantsArray) {
            JsonObject elemJson = helper.asJsonObject(element, "variants[] element", loc);
            String variantId = helper.getString(elemJson, "name");
            if (variants.containsKey(variantId)) {
                helper.throwInvalidField("name", loc, "Duplicated variant name '" + variantId + "'.");
            }
            JsonObject dimensionsObject = helper.getJsonObject(elemJson, "dimensions", loc);
            int x = helper.getInt(dimensionsObject, "x", "in variants[size{}]");
            int y = helper.getInt(dimensionsObject, "y", "in variants[size{}]");
            int z = helper.getInt(dimensionsObject, "z", "in variants[size{}]");
            if (x < 1 || y < 1 || z < 1) {
                helper.throwInvalidField("dimensions", loc, "x, y and z size should be > 1.");
            }
            Vec3i variantDimensions = new Vec3i(x, y, z);
            int level = 1;
            TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
            JsonArray levelsArray = helper.getJsonArray(elemJson, "levels", loc);
            loc = "in variants[levels[]]";
            for (JsonElement el : levelsArray) {
                elemJson = helper.asJsonObject(el, "levels[] element", loc);
                String schematicName = helper.getString(elemJson, "schematic", loc);
                ResourceLocation schematicRl = Ouat.modResource(LevelCultures.CULTURE_FOLDER_NAME + "/%s/builds/schematic/%s.nbt".formatted(cultureId, schematicName));
                Vec3i schematicSize = Utils.getSchematicDimensions(cultureId, variantId, schematicRl, resourceManager);
                if (!schematicSize.equals(variantDimensions)) {
                    helper.throwInvalidField("dimensions", "in variants[]", "Schematic '" + schematicName + ".nbt' of this build variant has dimensions " + schematicSize.toShortString() + ", but the build variant requires dimensions " + variantDimensions.toShortString() + ".");
                }
                BuildSchematic schematic = new BuildSchematic(schematicRl, null);
                schematics.put(level, schematic);
                ++level;
            }
            variants.put(variantId, new BuildVariant(variantId, variantDimensions, schematics));
        }
        return variants;
    } */

    public BuildVariant getRandomVariant(RandomSource rand) {
        BuildVariant[] variants = this.getBuildVariants().values().toArray(new BuildVariant[0]);
        return variants[rand.nextInt(variants.length)];
    }

    public Item getIconItem() {
        return iconItem;
    }
}
