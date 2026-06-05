package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * A Schematic is a set of blocks and entities, like vanilla StructureTemplate, which can be saved using a structure block. <br>
 * Can be seen as the construction plan of a structure. It does not contain any information about how the structure should be placed in world (position, rotation...)<br>
 * For performance issues, the content of its NBT structure file is only loaded when needed. <br>
 * A Schematic also defines Waypoints for NPCs to use, like beds, workstations...
 */
public class BuildSchematic {
    private final ResourceLocation resourceLocation; // The ResourceLocation of the Minecraft NBT file of the structure
    private final HashMap<Vec3i, Waypoint> waypoints; // Waypoints of this schematic

    public BuildSchematic(ResourceLocation resourceLocation, HashMap<Vec3i, Waypoint> waypoints) {
        this.resourceLocation = resourceLocation;
        this.waypoints = waypoints;
    }

    public void addWaypoint(Vec3i position, Waypoint waypoint) {
        // TODO warning handle multiple waypoints at same position
        waypoints.put(position, waypoint);
    }

    public @Nullable SchematicContent loadSchematic(ResourceManager resourceManager) {
        return SchematicContent.createFromDataPack(resourceLocation, resourceManager);
    }

    public ResourceLocation getResourceLocation() {
        return resourceLocation;
    }

    public HashMap<Vec3i, Waypoint> getWaypoints() {
        return waypoints;
    }
}
