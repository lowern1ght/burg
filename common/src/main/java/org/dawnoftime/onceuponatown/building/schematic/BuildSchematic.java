package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;

/**
 * A Schematic is a set of blocks and entities, like vanilla StructureTemplate, which can be saved using a structure block. <br>
 * Can be seen as the construction plan of a structure. It does not contain any information about how the structure should be placed in world (position, rotation...)<br>
 * For performance issues, the content of its NBT structure file is only loaded when needed. <br>
 * A Schematic also defines Waypoints for NPCs to use, like beds, workstations...
 */
public class BuildSchematic {
    private final ResourceLocation schematicResourceLocation; // The ResourceLocation of the Minecraft NBT file of the structure
    private final HashMap<Vec3i, Waypoint> waypoints = new HashMap<>(); // Waypoints of this schematic

    private BuildSchematic(ResourceLocation schematicResourceLocation) {
        this.schematicResourceLocation = schematicResourceLocation;
    }

    public static BuildSchematic createFromDataPack(ResourceManager resourceManager, ResourceLocation schematicResourceLocation, Vec3i requiredDimensions, String cultureId, String buildVariantId) throws CorruptedCultureException {
        Vec3i dimensions = getSchematicDimensions(resourceManager, schematicResourceLocation, cultureId, buildVariantId);
        if (dimensions.equals(requiredDimensions)) {
            return new BuildSchematic(schematicResourceLocation);
        } else {
            throw new CorruptedCultureException(cultureId, "Schematic at %s has dimensions [%s], but its build_variant '%s' requires a size of [%s]".formatted(schematicResourceLocation, dimensions.toShortString(), buildVariantId, requiredDimensions.toShortString()));
        }
    }

    private static Vec3i getSchematicDimensions(ResourceManager resourceManager, ResourceLocation schematicResourceLocation, String cultureId, String buildVariantId) throws CorruptedCultureException {
        String path = schematicResourceLocation.getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.'));
        try (InputStream inputStream = resourceManager.open(schematicResourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag sizeTag = tag.getList("size", 3);
            return new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        } catch (FileNotFoundException fileNotFoundException) {
            throw CorruptedCultureException.missingFile(cultureId, "Schematic", fileName, schematicResourceLocation);
        } catch (Exception e) {
            throw new CorruptedCultureException(cultureId, "Could not load schematic '%s' of the build_variant '%s'. Please verify the file at %s".formatted(fileName, buildVariantId, schematicResourceLocation));
        }
    }

    public void addWaypoint(Vec3i position, Waypoint waypoint) {
        waypoints.put(position, waypoint);
    }

    public @Nullable SchematicContent loadSchematic(ResourceManager resourceManager) {
        return SchematicContent.createFromDataPack(schematicResourceLocation, resourceManager);
    }

    public ResourceLocation getResourceLocation() {
        return schematicResourceLocation;
    }

    public HashMap<Vec3i, Waypoint> getWaypoints() {
        return waypoints;
    }
}
