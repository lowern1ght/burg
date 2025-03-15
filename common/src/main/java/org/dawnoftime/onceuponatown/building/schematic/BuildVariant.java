package org.dawnoftime.onceuponatown.building.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.CultureFileHelper;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.jetbrains.annotations.NotNull;
import oshi.util.tuples.Pair;

import java.util.HashMap;
import java.util.TreeMap;

/**
 * A BuildType's variant is a set of schematics for that BuildType. For example, you could have a house build type with two variants : one with a garden and one without.
 * All variants share the same behavior (number of inhabitants, number of workstations...) but are aesthetically different. <br>
 */
public class BuildVariant {
    private final String id;
    private final Vec3i dimensions;
    private final BuildSchematic[] buildSchematicArray; // Ordered by level

    public BuildVariant(String id, Vec3i dimensions, TreeMap<Integer, BuildSchematic> schematics) {
        this.id = id;
        this.dimensions = dimensions;
        this.buildSchematicArray = schematics.values().toArray(new BuildSchematic[0]);
    }

    public static @NotNull Pair<BuildVariant, String> createFromDataPack(String cultureId, JsonObject variantJson, CultureFileHelper helper, ResourceManager resourceManager) throws CorruptedCultureException {
        String loc = "in variants[]";
        /* Reading variant id */
        String variantId = helper.getString(variantJson, "name", loc);
        /* Reading variant dimensions */
        JsonObject dimsObject = helper.getJsonObject(variantJson, "size", loc);
        int x = helper.getInt(dimsObject, "x", "in variants[size{}]");
        int y = helper.getInt(dimsObject, "y", "in variants[size{}]");
        int z = helper.getInt(dimsObject, "z", "in variants[size{}]");
        if (x < 1 || y < 1 || z < 1) {
            helper.throwInvalidField("size", loc, "x, y and z sizes should be > 1.");
        }
        Vec3i variantDimensions = new Vec3i(x, y, z);
        /* Reading optional shape */
        JsonElement shapeElem = variantJson.get("shape");
        String shape = shapeElem == null ? null : helper.getString(variantJson, "shape", loc);
        /* Reading variant levels */
        JsonArray levelsArray = helper.getJsonArray(variantJson, "levels", loc);
        TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
        loc = "in variants[levels[]]";
        int level = 1;
        for (JsonElement el : levelsArray) {
            variantJson = helper.asJsonObject(el, "variant[levels[]] element", loc);
            /* Reading variant schematic id */
            String schematicName = helper.getString(variantJson, "schematic", loc);
            ResourceLocation schematicRl = Ouat.modResource(DataHandler.CULTURES_FOLDER_NAME + "/%s/schematics/%s.nbt".formatted(cultureId, schematicName));
            Vec3i schematicDimensions = Utils.getSchematicDimensions(cultureId, variantId, schematicRl, resourceManager);
            if (!schematicDimensions.equals(variantDimensions)) {
                helper.throwInvalidField("size", "in variants[]", "Schematic '" + schematicName + ".nbt' of a build variant has dimensions " + schematicDimensions.toShortString() + ", but the build variant requires dimensions " + variantDimensions.toShortString());
            }
            // TODO Read waypoints
            BuildSchematic schematic = new BuildSchematic(schematicRl, null);
            schematics.put(level, schematic);
            ++level;
        }
        return new Pair<>(new BuildVariant(variantId, variantDimensions, schematics), shape);
    }

    public BuildSchematic getSchematic(int level) {
        return buildSchematicArray[level - 1];
    }

    public HashMap<Vec3i, Waypoint> getWaypoints(int level) {
        return this.getSchematic(level).getWaypoints();
    }

    public ResourceLocation getSchematicRl(int level) {
        return this.getSchematic(level).getResourceLocation();
    }

    public SchematicContent getSchematicContent(ResourceManager resourceManager, int level) {
        return this.getSchematic(level).loadSchematic(resourceManager);
    }

    public String getId() {
        return id;
    }

    public Vec3i getDimensions() {
        return dimensions;
    }
}
