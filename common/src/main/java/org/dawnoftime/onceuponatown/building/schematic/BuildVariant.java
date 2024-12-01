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
import oshi.util.tuples.Pair;
import oshi.util.tuples.Triplet;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * This class contains all the information required to build one of the instance of a BuildType,
 * such as the id of the building and a List of BuildSchematic (one for each level).
 */
public class BuildVariant {
    private final String name;
    private final Vec3i size;
    private final BuildSchematic[] buildSchematicArray;

    private BuildVariant(String name, Vec3i size, TreeMap<Integer, BuildSchematic> buildSchematicList){
        this.name = name;
        this.size = size;
        this.buildSchematicArray = buildSchematicList.values().toArray(new BuildSchematic[0]);
    }

    /**
     * Read the schematic plan (named with the variant name) that contains :<p>
     * - the size of the building (the schematics must all have the same size).<p>
     * - the list of level with the corresponding schematic path and its waypoints.
     * @param resourceManager Resource manager used to load the file.
     * @param buildResource The resource location of the file being loaded.
     * @param cultureName The name of the culture that owns this build_variant. Only used to throw specific error description.
     * @return Either a Pair BuildType name / BuildSchematicPlan if all the files could be loaded, null otherwise.
     */
    public static @Nullable Triplet<String, BuildVariant, String> createFromJson(ResourceManager resourceManager, ResourceLocation buildResource, String cultureName){
        try {
            Resource resource = resourceManager.getResource(buildResource).orElseThrow();
            try (Reader reader = resource.openAsReader()) {
                String path = buildResource.getPath();
                String buildVariantName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
                JsonObject buildVariantJson = GsonHelper.parse(reader);

                // Reading the build_type.
                JsonElement elem = CultureManager.tryGet(buildVariantJson, "build_type", "build_variant", cultureName, buildVariantName + ".json", buildResource);
                String buildTypeName = elem.getAsString();

                // Reading the size.
                elem = CultureManager.tryGet(buildVariantJson, "size", "build_variant", cultureName, buildVariantName + ".json", buildResource);
                JsonObject subObject = elem.getAsJsonObject();
                int x = CultureManager.tryGet(subObject, "x", " in the section 'size'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsInt();
                int y = CultureManager.tryGet(subObject, "y", " in the section 'size'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsInt();
                int z = CultureManager.tryGet(subObject, "z", " in the section 'size'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsInt();
                Vec3i size = new Vec3i(x, y, z);

                // Reading each level.
                elem = CultureManager.tryGet(buildVariantJson, "levels", "build_variant", cultureName, buildVariantName + ".json", buildResource);
                JsonArray array = elem.getAsJsonArray();
                TreeMap<Integer, BuildSchematic> schematics = new TreeMap<>();
                for(JsonElement arrayElem : array){
                    subObject = arrayElem.getAsJsonObject();
                    int level = CultureManager.tryGet(subObject, "level", " in an object in the section 'levels'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsInt();
                    String schematicName = CultureManager.tryGet(subObject, "schematic", " in an object in the section 'levels'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsString();
                    BuildSchematic schematic = BuildSchematic.create(resourceManager, Ouat.createOuatResource("cultures/%s/builds/schematic/%s.nbt".formatted(cultureName, schematicName)), size, cultureName, buildVariantName);
                    // TODO Do the code that loads the waypoints.
                    // for each waypoints loaded : schematic.addWaypoint();
                    schematics.put(level, schematic);
                }

                // Read the bonus info : the shape.
                elem = buildVariantJson.get("shape");
                String shape = elem != null ? elem.getAsString() : null;

                // Finally we check if the BuildSchematic exists for each list level, and have the correct size.
                if (schematics.isEmpty()){
                    throw new CorruptedCultureException(cultureName, "Failed to register a build_variant. You need to define at least the first level. Please check the file: %s".formatted(buildResource));
                }
                if (schematics.firstKey() < 1) {
                    throw new CorruptedCultureException(cultureName, "Failed to register a build_variant. The lowest level must be 1. Please check the file: %s".formatted(buildResource));
                }
                if (!IntStream.rangeClosed(1, schematics.lastKey()).allMatch(schematics::containsKey)) {
                    throw new CorruptedCultureException(cultureName, "Failed to register a build_variant. You need to define each level from 1 to the maximum level. Please check the file: %s".formatted(buildResource));
                }
                return new Triplet<>(buildTypeName, new BuildVariant(buildVariantName, size, schematics), shape);
            }
        } catch (IOException | JsonParseException e) {
            Ouat.error("Culture [%s]: Could not read a build_variant file. Maybe you made a typo in the file: %s".formatted(cultureName, buildResource));
        } catch (IllegalStateException e) {
            Ouat.error("Culture [%s]: Could not read a build_variant file. The structure of the json is not correct in the file: %s".formatted(cultureName, buildResource));
        } catch (CorruptedCultureException e){
            Ouat.error(e.getMessage());
        }
        return null;
    }

    public BuildSchematic getBuildSchematic(int level) {
        return this.buildSchematicArray[level - 1];
    }

    public HashMap<Vec3i, Waypoint> getWaypoints(int level){
        return this.getBuildSchematic(level).getWaypoints();
    }

    public ResourceLocation getSchematicResource(int level){
        return this.getBuildSchematic(level).getSchematicResourceLocation();
    }

    public SchematicContent getSchematic(ResourceManager resourceManager, int level){
        return this.getBuildSchematic(level).load(resourceManager);
    }

    public String getName() {
        return this.name;
    }

    public Vec3i getSize(){
        return this.size;
    }
}
