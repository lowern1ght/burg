package org.dawnoftime.onceuponatown.building.type;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.Specialization;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class BuildingType extends BuildType {
    public final BuildingPurpose purpose;
    private final HashMap<Specialization, Integer> specializationsGain = new HashMap<>();
    private final Item iconItem;

    protected BuildingType(String buildTypeId, int weight, List<BuildLevel> levels, BuildingPurpose purpose, Item iconItem) {
        super(buildTypeId, weight, levels);
        this.iconItem = iconItem;
        this.purpose = purpose;
    }

    public static @Nullable BuildingType createFromJson(ResourceManager resourceManager, ResourceLocation buildingRl, String cultureId) {
        try (Reader reader = resourceManager.getResource(buildingRl).orElseThrow().openAsReader()) {
            String fileName = buildingRl.toDebugFileName();
            String path = buildingRl.getPath();
            String buildTypeId = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
            JsonObject buildTypeJson = GsonHelper.parse(reader);
            // Id
            String id = buildTypeJson.get("id").getAsString();
            if (id == null || !id.equals(buildTypeId)) {
                throw new CorruptedCultureException(cultureId, "BuildingType id '%s' does not match its json file name ('%s')".formatted(id, buildTypeId));
            }
            // Purpose
            String purposeId = buildTypeJson.get("category").getAsString().toUpperCase();
            BuildingPurpose purpose = null;
            try {
                purpose = BuildingPurpose.valueOf(purposeId);
            } catch (Exception e) {
                throw new CorruptedCultureException(cultureId, "BuildingType '%s' has undefined or wrong building category".formatted(id));
            }
            // Icon item
            JsonElement iconItemJson = buildTypeJson.get("icon");
            Item iconItem;
            if (iconItemJson != null) {
                iconItem = Ouat.COMMON.getItem(new ResourceLocation(iconItemJson.getAsString()));
            } else {
                iconItem = Items.OAK_PLANKS;
            }
            // Clutter
            int clutter = buildTypeJson.get("weight").getAsInt();
            if (clutter < 0) {
                throw new CorruptedCultureException(cultureId, fileName, "building type weight", "weight can not be negative");
            }
            // Levels
            List<BuildLevel> levels = new ArrayList<>();
            var levelArray = buildTypeJson.getAsJsonArray("levels");
            if (levelArray == null) {
                throw new CorruptedCultureException(cultureId, fileName, "building type levels", "Missing levels definition");
            }
            int levelIndex = 1;
            for (int i = 0; i < levelArray.size(); ++i) {
                var jsonObject = levelArray.get(i).getAsJsonObject();
                int eraNeeded = jsonObject.get("required_era").getAsInt();
                if (eraNeeded < 0) { // Error : can not be negative
                    throw new CorruptedCultureException(cultureId, fileName, "building type level required_era", "required_era can not be negative");
                }
                /*
                int xpGain = jsonObject.get("xp_gain").getAsInt();
                if (xpGain <= 0) { // Error : can not be null or negative
                    throw new CorruptedCultureException(cultureId, fileName, "building type level xp_gain", "xp_gain can not be negative");
                }
                 */
                ++levelIndex;
            }
            return new BuildingType(buildTypeId, clutter, levels, purpose, iconItem);
        } catch (IOException e) {

            throw new CorruptedCultureException(cultureId, "Could not read a build_type json file. Check the file at : " + buildingRl);
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
