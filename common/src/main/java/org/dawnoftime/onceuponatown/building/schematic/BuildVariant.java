package org.dawnoftime.onceuponatown.building.schematic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Triplet;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * A BuildType's variant is a set of schematics for that BuildType. For example, you could have a house build type with two variants : one with a garden and one without.
 * All variants share the same behavior (number of inhabitants, number of workstations...) but are aesthetically different. <br>
 */
public class BuildVariant {
    private final String id;
    private final Vec3i dimensions;
    private final BuildSchematic[] buildSchematicArray;

    private BuildVariant(String id, Vec3i dimensions, TreeMap<Integer, BuildSchematic> buildSchematicList) {
        this.id = id;
        this.dimensions = dimensions;
        this.buildSchematicArray = buildSchematicList.values().toArray(new BuildSchematic[0]);
    }

    /**
     * Creates a BuildVariant from the data pack. <br>
     * Reads the corresponding Json file.
     *
     * @param buildVariantRl The ResourceLocation of the BuildVariant being loaded.
     * @param cultureId      The name of the culture that owns this build_variant. Only used to add specific error description.
     * @return The BuildType's name + the loaded BuildVariant + optional shape if the files could be loaded, null otherwise.
     */
    public static @Nullable Triplet<String, BuildVariant, String> createFromDataPack(ResourceManager resourceManager, ResourceLocation buildVariantRl, String cultureId) {
        try (Reader reader = resourceManager.openAsReader(buildVariantRl)) {
            String path = buildVariantRl.getPath();
            String buildVariantId = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
            String fileName = buildVariantId + ".json";
            JsonObject objectJson = GsonHelper.parse(reader);
            var variantJson = new CultureManager.CultureJsonFile(cultureId, "build_variant", objectJson, buildVariantRl, fileName);

            // Reading the build_type.
            JsonElement elem = variantJson.tryGet("build_type");
            String buildTypeName = elem.getAsString();

            // Reading the size.
            elem = variantJson.tryGet("size");
            JsonObject subObject = elem.getAsJsonObject();
            int x = variantJson.tryGet(subObject, "x", "in the section 'size'").getAsInt();
            int y = variantJson.tryGet(subObject, "y", "in the section 'size'").getAsInt();
            int z = variantJson.tryGet(subObject, "z", "in the section 'size'").getAsInt();
            Vec3i size = new Vec3i(x, y, z);

            // Reading each level.
            elem = variantJson.tryGet("levels");
            JsonArray array = elem.getAsJsonArray();
            TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
            for (JsonElement arrayElem : array) {
                subObject = arrayElem.getAsJsonObject();
                int level = variantJson.tryGet(subObject, "level", "in an object in the section 'levels'").getAsInt();
                String schematicName = variantJson.tryGet(subObject, "schematic", " in an object in the section 'levels'").getAsString();
                BuildSchematic schematic = BuildSchematic.createFromDataPack(resourceManager, Ouat.modResource(CultureManager.CULTURE_FOLDER_NAME + "/%s/builds/schematic/%s.nbt".formatted(cultureId, schematicName)), size, cultureId, buildVariantId);
                // TODO Do the code that loads the waypoints.
                // for each waypoints loaded : schematic.addWaypoint();
                schematics.put(level, schematic);
            }

            // Read the bonus info : the shape.
            elem = objectJson.get("shape");
            String shape = elem != null ? elem.getAsString() : null;

            // Finally we check if the BuildSchematic exists for each list level, and have the correct size.
            if (schematics.isEmpty()) {
                throw new CorruptedCultureException(cultureId, "Failed to register the build_variant '%s' because it has no levels. It should have at least one level. Check the file at : %s".formatted(buildVariantId, buildVariantRl));
            }
            if (schematics.firstKey() < 1) {
                throw new CorruptedCultureException(cultureId, "Failed to register the build_variant '%s'. The lowest level must be 1. Check the file at : %s".formatted(buildVariantId, buildVariantRl));
            }
            if (!IntStream.rangeClosed(1, schematics.lastKey()).allMatch(schematics::containsKey)) {
                throw new CorruptedCultureException(cultureId, "Failed to register the build_variant '%s'. You need to define each level from 1 to the maximum level. Check the file at : %s".formatted(buildVariantId, buildVariantRl));
            }
            return new Triplet<>(buildTypeName, new BuildVariant(buildVariantId, size, schematics), shape);

        } catch (IOException | JsonParseException e) {
            // Why no throw ?
            Ouat.error(new CorruptedCultureException(cultureId, "Could not read a build_variant file. Maybe there is a typo ? Check the file at : %s".formatted(cultureId, buildVariantRl)).getMessage());
        } catch (IllegalStateException e) {
            // Why no throw ?
            Ouat.error(new CorruptedCultureException(cultureId, "Could not read a build_variant file because its json structure is incorrect. Check the file at : %s".formatted(cultureId, buildVariantRl)).getMessage());
        } catch (CorruptedCultureException e) {
            // Why no throw ?
            Ouat.error(e.getMessage());
        }
        return null;
    }

    public BuildSchematic getBuildSchematic(int level) {
        return buildSchematicArray[level - 1];
    }

    public HashMap<Vec3i, Waypoint> getWaypoints(int level) {
        return this.getBuildSchematic(level).getWaypoints();
    }

    public ResourceLocation getSchematicResourceLocation(int level) {
        return this.getBuildSchematic(level).getResourceLocation();
    }

    public SchematicContent getSchematicContent(ResourceManager resourceManager, int level) {
        return this.getBuildSchematic(level).loadSchematic(resourceManager);
    }

    public String getId() {
        return id;
    }

    public Vec3i getDimensions() {
        return dimensions;
    }
}
