package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Ouat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.IdMapper;
import net.minecraft.core.Vec3i;
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
    /**
     * The list of blocks in this building
     */
    private final List<BlockInfo> blocks = new ArrayList<>();
    /**
     * The list of entities in this building
     */
    private final List<EntityInfo> entities = new ArrayList<>();

    private BuildSchematic(ResourceLocation schematicResourceLocation){
        this.schematicResourceLocation = schematicResourceLocation;
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
            throw new CorruptedCultureException("Culture [%s]: Error loading a schematic for the build_variant '%s'. Could not load the file: %s".formatted(cultureName, buildVariantName, schematicResourceLocation));
        }
        if(requiredSize.equals(size)){
            return new BuildSchematic(schematicResourceLocation);
        }else{
            throw new CorruptedCultureException("Culture [%s]: A schematic loaded has a size of [%s], instead of the size [%s] defined in the build_variant '%s'. Check this file: %s".formatted(cultureName, size.toShortString(), requiredSize.toShortString(), buildVariantName, schematicResourceLocation));
        }
    }

    public void addWaypoint(Vec3i position, Waypoint waypoint){
        this.waypoints.put(position, waypoint);
    }

    private BuildSchematic(ResourceLocation structurePath, HolderGetter<Block> blockGetter, CompoundTag structureTag) {
        this.schematicResourceLocation = structurePath;
        this.readSchematic(blockGetter, structureTag);
    }

    /**
     * Creates a building plan from its RL, replaces the constructor. Returns null if the file could not be loaded.
     * @param structurePath The structure NBT file path of the desired structure
     * @param resourceManager Resource manager
     * @return a new building plan.
     */
    public static @Nullable BuildSchematic create(ResourceLocation structurePath, ResourceManager resourceManager) {
        FileToIdConverter converter = new FileToIdConverter("structures", ".nbt");
        ResourceLocation resourceLocation = converter.idToFile(structurePath);
        try (InputStream inputStream = resourceManager.open(resourceLocation)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            BuildSchematic constructionPlan = new BuildSchematic(structurePath, BuiltInRegistries.BLOCK.asLookup(), tag);
            return constructionPlan.withoutAirBlocks();
        } catch (FileNotFoundException fileNotFoundException) {
            Ouat.LOG.error("Structure not found {}", resourceLocation, fileNotFoundException);
            return null;
        } catch (Throwable throwable) {
            Ouat.LOG.error("Could not load structure {}", resourceLocation, throwable);
            return null;
        }
    }

    /**
     * Read the structure NBT file, initialize building plan blocks and entities
     * @param structureTag The structure NBT tag
     */
    private void readSchematic(HolderGetter<Block> blockGetter, CompoundTag structureTag) {
        // Extracting dimensions
        ListTag sizeTag = structureTag.getList("size", 3);
        // Extracting entities
        ListTag entitiesTag = structureTag.getList("entities", 10);
        for(int i = 0; i < entitiesTag.size(); ++i) {
            CompoundTag entityTag = entitiesTag.getCompound(i);
            ListTag posTag = entityTag.getList("pos", 6);
            Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            ListTag blockPosTag = entityTag.getList("blockPos", 3);
            BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
            if (entityTag.contains("nbt")) {
                CompoundTag entityNBT = entityTag.getCompound("nbt");
                this.entities.add(new EntityInfo(pos, blockPos, entityNBT));
            }
        }
        // Extracting blocks
        ListTag blocksTag = structureTag.getList("blocks", 10);
        // Extracting palette
        ListTag paletteTag;
        if (structureTag.contains("palettes", 9)) {
            ListTag palettesTag = structureTag.getList("palettes", 9);
            paletteTag = palettesTag.getList(0);
        } else {
            paletteTag = structureTag.getList("palette", 10);
        }
        buildBlocksList(blockGetter, paletteTag, blocksTag);
    }

    /**
     * Initialize the blocks list
     * @param paletteTag The structure blocks palette
     * @param blocksTag The structure blocks tag
     */
    private void buildBlocksList(HolderGetter<Block> blockGetter, ListTag paletteTag, ListTag blocksTag) {
        Palette palette = new Palette();
        for(int i = 0; i < paletteTag.size(); ++i) {
            palette.addMapping(NbtUtils.readBlockState(blockGetter, paletteTag.getCompound(i)), i);
        }
        List<BlockInfo> blockInfoList = new ArrayList<>();
        for(int i = 0; i < blocksTag.size(); ++i) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            ListTag posTag = blockTag.getList("pos", 3);
            BlockPos blockPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            BlockState blockState = palette.stateFor(blockTag.getInt("state"));
            CompoundTag blockNBT;
            if (blockTag.contains("nbt")) {
                blockNBT = blockTag.getCompound("nbt");
            } else {
                blockNBT = null;
            }
            BlockInfo blockInfo = new BlockInfo(blockPos, blockState, blockNBT);
            blockInfoList.add(blockInfo);
        }
        ConstructionUtils.sortBlocks(blockInfoList);
        this.blocks.addAll(blockInfoList);
    }

    /**
     * @return This building plan without air blocks
     */
    public BuildSchematic withoutAirBlocks() {
        List<BlockInfo> toRemove = new ArrayList<>();
        for (BlockInfo blockInfo : this.blocks) {
            if (blockInfo.state().isAir()) {
                toRemove.add(blockInfo);
            }
        }
        this.blocks.removeAll(toRemove);
        return this;
    }

    public int numberOfBlocksInPlan() {
        return this.blocks.size();
    }

    public Block getBlock(int index) {
        return this.blocks.get(index).state().getBlock();
    }

    public BlockPos getBlockPos(int index) {
        return this.blocks.get(index).pos();
    }

    public BlockState getBlockState(int index) {
        return this.blocks.get(index).state();
    }

    public CompoundTag getBlockNBT(int index) {
        return this.blocks.get(index).nbt();
    }

    public List<EntityInfo> getEntities() {
        return this.entities;
    }

    public List<BlockInfo> getBlocks() {
        return this.blocks;
    }

    public ResourceLocation getStructurePath() {
        return this.schematicResourceLocation;
    }

    public HashMap<Vec3i, Waypoint> getWaypoints() {
        return this.waypoints;
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
