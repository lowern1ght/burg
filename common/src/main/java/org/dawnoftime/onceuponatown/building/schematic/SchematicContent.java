package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.building.SliceBuild;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.construction.BlockInfo;
import org.dawnoftime.onceuponatown.construction.ConstructionUtils;
import org.dawnoftime.onceuponatown.construction.EntityInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

/**
 * Structure template similar to the vanilla one, but adapted to the mod needs.
 */
public class SchematicContent {
    private final List<BlockInfo> blocks = new ArrayList<>(); // Blocks (pos, state, nbt) in the schematic
    private final List<EntityInfo> entities = new ArrayList<>(); // Entities (pos, state, nbt) in the schematic
    private Vec3i dimensions = Vec3i.ZERO; // XYZ dimensions of the schematic

    private SchematicContent() {
    }

    /**
     * Creates a Schematic from its location in the data pack.
     *
     * @return The loaded Schematic or null if an exception occurred.
     */
    public static @Nullable SchematicContent createFromDataPack(ResourceLocation schematicRl, ResourceManager resourceManager) {
        try (InputStream inputStream = resourceManager.open(schematicRl)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            SchematicContent schematic = new SchematicContent();
            schematic.readSchematicNbtTag(BuiltInRegistries.BLOCK.asLookup(), tag);
            return schematic; // Why this ? : return schematic.withoutAirBlocks();
        } catch (FileNotFoundException fnfe) {
            String msg = "Corrupted culture. Could not find a schematic file. It should be located here %s".formatted(schematicRl);
            Ouat.LOG.error(msg, fnfe);
            return null;
        } catch (Exception e) {
            String msg = "Corrupted culture. Could not load a schematic file located at %s".formatted(schematicRl);
            Ouat.LOG.error(msg, e);
            return null;
        }
    }

    /**
     * Creates an array from this SchematicContent, that contains one Schematic for each slice on the Z axis.
     *
     * @param shape SliceBuildShape of the schematic, used to remove the useless top and bottom blocks.
     * @return The array with the instance of SchematicContent corresponding to the slices.
     */
    public SchematicContent[] asSliceArray(SliceBuildType.SliceBuildShape shape) {
        SchematicContent[] sliceArray = new SchematicContent[this.dimensions.getZ()];
        int[] minY = new int[this.dimensions.getZ()];
        int[] maxY = new int[this.dimensions.getZ()];
        for (int z = 0; z < this.dimensions.getZ(); z++) {
            SchematicContent temp = new SchematicContent();
            temp.dimensions = new Vec3i(this.dimensions.getX(), this.dimensions.getY(), 1);
            sliceArray[z] = temp;
            minY[z] = shape.getMinYForSliceSchematic(z);
            maxY[z] = shape.getMaxYForSliceSchematic(z, this.dimensions.getZ(), this.dimensions.getY());
        }
        for (BlockInfo info : this.blocks) {
            int patternIndex = info.pos().getZ();
            if (info.pos().getY() >= minY[patternIndex] && info.pos().getY() <= maxY[patternIndex]) {
                sliceArray[patternIndex].blocks.add(info.move(0, -minY[patternIndex], -patternIndex));
            }
        }
        for (EntityInfo info : this.entities) {
            int patternIndex = info.pos().getZ();
            if (info.pos().getY() >= minY[patternIndex] && info.pos().getY() <= maxY[patternIndex]) {
                sliceArray[patternIndex].entities.add(info.move(0, -minY[patternIndex], -patternIndex));
            }
        }
        return sliceArray;
    }

    /**
     * Gets the build slices of the build in parameter, and merge them to create a BuildSchematic.
     * The slices are moved to the correct Y and rotated in the build's direction.
     */
    public static SchematicContent getSliceBuildSchematic(SliceBuild build, ResourceManager resourceManager) {
        // First, we load the needed schematics.
        HashMap<String, SchematicContent[]> sliceMap = new HashMap<>();
        build.getBuildVariantMap().forEach((variantName, pair) -> {
            SchematicContent schematicContent = SchematicContent.createFromDataPack(pair.getA().getSchematicResourceLocation(build.getLevel()), resourceManager);
            if (schematicContent != null) {
                sliceMap.put(variantName, schematicContent.asSliceArray(pair.getB()));
            }
        });
        // Now we can build the schematic using the slices.
        BlockPos originPos = build.getOriginPos();
        int originY = originPos.getY();
        int patternLength = ((SliceBuildType) build.getBuildType()).getPatternLength();
        SliceBuild.SliceProperty[] yShape = build.getYShape();
        SchematicContent schematic = new SchematicContent();
        for (int yIndex = 0; yIndex < yShape.length; yIndex++) {
            SliceBuild.SliceProperty slice = yShape[yIndex];
            int offsetY = slice.y() - originY;
            List<BlockInfo> blocks = sliceMap.get(slice.buildVariantId())[yIndex % patternLength].getBlocks();
            if (slice.shape() == SliceBuildType.SliceBuildShape.STAIRS_INVERTED) {
                blocks.replaceAll(BlockInfo::inverse);
            }
            int finalYIndex = yIndex;
            schematic.blocks.addAll(blocks.stream().map(blockInfo -> blockInfo.move(0, offsetY, finalYIndex)).toList());
            List<EntityInfo> entities = sliceMap.get(slice.buildVariantId())[yIndex % patternLength].getEntities();
            schematic.entities.addAll(entities.stream().map(entityInfo -> entityInfo.move(0, offsetY, finalYIndex)).toList());
        }
        schematic.dimensions = new Vec3i(build.getNorthSizeX(), build.getSizeY(), yShape.length);
        return schematic;
    }

    /**
     * Rotates this Schematic towards a Direction by rotating each individual block and entity, and updating this Schematic's dimensions.
     */
    public SchematicContent rotate(Direction direction) {
        blocks.replaceAll(blockInfo -> blockInfo.rotateInBuild(direction, dimensions.getX(), dimensions.getZ()));
        entities.replaceAll(entityInfo -> entityInfo.rotate(direction, dimensions.getX(), dimensions.getZ()));
        if (direction.getAxis() == Direction.Axis.X) {
            dimensions = new Vec3i(dimensions.getZ(), dimensions.getY(), dimensions.getX());
        }
        return this;
    }

    /**
     * Initializes this Schematic by reading the structure NBT file and building the blocks and entities lists.
     *
     * @param structureTag The structure NBT tag.
     */
    private void readSchematicNbtTag(HolderGetter<Block> blockGetter, CompoundTag structureTag) {
        // Extracting dimensions
        ListTag sizeTag = structureTag.getList("size", 3);
        dimensions = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        // Extracting entities
        ListTag entitiesTag = structureTag.getList("entities", 10);
        for (int i = 0; i < entitiesTag.size(); ++i) {
            CompoundTag entityTag = entitiesTag.getCompound(i);
            ListTag posTag = entityTag.getList("vec3", 6);
            Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            ListTag blockPosTag = entityTag.getList("pos", 3);
            BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
            if (entityTag.contains("entityNbt")) {
                CompoundTag entityNBT = entityTag.getCompound("entityNbt");
                entities.add(new EntityInfo(pos, blockPos, entityNBT));
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
        this.createBlocksList(blockGetter, paletteTag, blocksTag);
    }

    /**
     * Initialize the blocks list.
     *
     * @param paletteTag The structure blocks palette.
     * @param blocksTag  The structure blocks tag.
     */
    private void createBlocksList(HolderGetter<Block> blockGetter, ListTag paletteTag, ListTag blocksTag) {
        Palette palette = new Palette();
        for (int i = 0; i < paletteTag.size(); ++i) {
            palette.addMapping(NbtUtils.readBlockState(blockGetter, paletteTag.getCompound(i)), i);
        }
        List<BlockInfo> blockInfoList = new ArrayList<>();
        for (int i = 0; i < blocksTag.size(); ++i) {
            CompoundTag blockTag = blocksTag.getCompound(i);
            ListTag posTag = blockTag.getList("pos", 3);
            BlockPos blockPos = new BlockPos(posTag.getInt(0), posTag.getInt(1), posTag.getInt(2));
            BlockState blockState = palette.stateFor(blockTag.getInt("state"));
            CompoundTag blockNBT;
            if (blockTag.contains("entityNbt")) {
                blockNBT = blockTag.getCompound("entityNbt");
            } else {
                blockNBT = null;
            }
            BlockInfo blockInfo = new BlockInfo(blockPos, blockState, blockNBT);
            blockInfoList.add(blockInfo);
        }
        ConstructionUtils.sortBlocks(blockInfoList);
        blocks.addAll(blockInfoList);
    }

    /**
     * @return This Schematic without air blocks.
     */
    public SchematicContent withoutAirBlocks() {
        List<BlockInfo> toRemove = new ArrayList<>();
        for (BlockInfo blockInfo : blocks) {
            if (blockInfo.state().isAir()) {
                toRemove.add(blockInfo);
            }
        }
        blocks.removeAll(toRemove);
        return this;
    }

    public Block getBlock(int i) {
        return blocks.get(i).state().getBlock();
    }

    public BlockPos getBlockPos(int i) {
        return blocks.get(i).pos();
    }

    public BlockState getBlockState(int i) {
        return blocks.get(i).state();
    }

    public CompoundTag getBlockNBT(int i) {
        return blocks.get(i).nbt();
    }

    public List<EntityInfo> getEntities() {
        return entities;
    }

    public List<BlockInfo> getBlocks() {
        return blocks;
    }

    public Vec3i getDimensions() {
        return dimensions;
    }

    private static class Palette implements Iterable<BlockState> {
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
