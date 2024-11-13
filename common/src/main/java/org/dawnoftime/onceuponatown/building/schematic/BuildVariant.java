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
import org.dawnoftime.onceuponatown.Utils;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.dawnoftime.onceuponatown.culture.CultureManager;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * This class contains all the information required to build one of the instance of a BuildType,
 * such as the id of the building and a List of BuildSchematic (one for each level).
 */
public class BuildVariant {
    private final String name;
    private final Vec3i size;
    private final BuildSchematic[] buildSchematicArray;

    private BuildVariant(String name, Vec3i size, List<BuildSchematic> buildSchematicList){
        this.name = name;
        this.size = size;
        this.buildSchematicArray = buildSchematicList.toArray(new BuildSchematic[0]);
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
    public static @Nullable Pair<String, BuildVariant> createFromJson(ResourceManager resourceManager, ResourceLocation buildResource, String cultureName){
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
                ArrayList<BuildSchematic> schematics = new ArrayList<>();
                for(JsonElement arrayElem : array){
                    subObject = elem.getAsJsonObject();
                    int level = CultureManager.tryGet(subObject, "level", " in an object in the section 'levels'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsInt();
                    String schematic = CultureManager.tryGet(subObject, "schematic", " in an object in the section 'levels'", "build_variant", cultureName, buildVariantName + ".json", buildResource).getAsString();
                    // TODO Do the code that loads the waypoints.
                    //schematics.add(new BuildSchematic())
                }

                // Finally we check if the BuildSchematic exists for each list level, and have the correct size.
                // TODO Do the size checker.

                return new Pair<>(buildTypeName, new BuildVariant(buildVariantName, size, schematics));
            }
        } catch (IOException | JsonParseException e) {
            Ouat.error("Could not read a build_variant json file. Maybe you made a typo in this file: " + buildResource);
        } catch (IllegalStateException e) {
            Ouat.error("Could not read a build_variant json file. The organisation of the json is not correct in this file: " + buildResource);
        } catch (CorruptedCultureException e){
            Ouat.error(e.getMessage());
        }
        return null;
    }

    public List<Waypoint> getWaypoints(int level){
        return this.buildSchematicArray[level - 1].getWaypoints();
    }

    public String getName() {
        return this.name;
    }
}
