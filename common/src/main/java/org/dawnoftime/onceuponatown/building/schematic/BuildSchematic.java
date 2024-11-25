package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.*;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Ouat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.construction.ConstructionUtils;
import org.dawnoftime.onceuponatown.construction.EntityInfo;
import org.dawnoftime.onceuponatown.culture.CorruptedCultureException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a structure NBT file.<br>
 * Can be seen as the construction plan of a structure.<br>
 * Shared by all structures which are described by the same NBT file.<br>
 * Does not contain any information about how the structure should be placed in world (position, rotation...).<br>
 * Warning ! The content of the NBT is only loaded when needed, only the resourceLocation is kept in cache.
 */
public class BuildSchematic {

    private final ResourceLocation schematicResourceLocation;
    private final HashMap<Vec3i, Waypoint> waypoints = new HashMap<>();

    private BuildSchematic(ResourceLocation schematicResourceLocation){
        this.schematicResourceLocation = schematicResourceLocation;
    }

    public ResourceLocation getSchematicResourceLocation() {
        return this.schematicResourceLocation;
    }

    public static BuildSchematic create(ResourceManager resourceManager, ResourceLocation schematicResourceLocation, Vec3i requiredSize, String cultureName, String buildVariantName) throws CorruptedCultureException{
        Vec3i size;
        try (InputStream inputStream = resourceManager.open(schematicResourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            ListTag sizeTag = tag.getList("size", 3);
            size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        } catch (FileNotFoundException fileNotFoundException) {
            String path = schematicResourceLocation.getPath();
            throw CorruptedCultureException.missingFile(cultureName, "schematic", path.substring(path.lastIndexOf('/') + 1, path.lastIndexOf('.')), schematicResourceLocation);
        } catch (Throwable throwable) {
            throw new CorruptedCultureException(cultureName, "Error loading a schematic for the build_variant '%s'. Could not load the file: %s".formatted(buildVariantName, schematicResourceLocation));
        }
        if(requiredSize.equals(size)){
            return new BuildSchematic(schematicResourceLocation);
        }else{
            throw new CorruptedCultureException(cultureName, "A schematic loaded has a size of [%s], instead of the size [%s] defined in the build_variant '%s'. Check this file: %s".formatted(size.toShortString(), requiredSize.toShortString(), buildVariantName, schematicResourceLocation));
        }
    }

    public void addWaypoint(Vec3i position, Waypoint waypoint){
        this.waypoints.put(position, waypoint);
    }

    public HashMap<Vec3i, Waypoint> getWaypoints() {
        return this.waypoints;
    }

    public @Nullable SchematicContent load(ResourceManager resourceManager){
        return SchematicContent.create(this.schematicResourceLocation, resourceManager);
    }

    static class Palette implements Iterable<BlockState> {
        public static final BlockState DEFAULT_BLOCK_STATE = Blocks.AIR.defaultBlockState();
        private final IdMapper<BlockState> ids = new IdMapper<>(16);
        private int lastId;

        public int idFor(BlockState state) {
            int i = this.ids.getId(state);
            if (i == -1) {
                i = this.lastId++;
                this.ids.addMapping(state, i);
            }

            return i;
        }

        public BlockState stateFor(int id) {
            BlockState blockstate = this.ids.byId(id);
            return blockstate == null ? DEFAULT_BLOCK_STATE : blockstate;
        }

        public @NotNull Iterator<BlockState> iterator() {
            return this.ids.iterator();
        }

        public void addMapping(BlockState state, int id) {
            this.ids.addMapping(state, id);
        }
    }
}
