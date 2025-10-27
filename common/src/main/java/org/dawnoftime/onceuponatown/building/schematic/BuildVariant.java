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
