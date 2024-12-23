package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Vec3i;
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
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SchematicContent {

    private final List<BlockInfo> blocks = new ArrayList<>();
    private final List<EntityInfo> entities = new ArrayList<>();
    private Vec3i size = Vec3i.ZERO;

    private SchematicContent(){}

    /**
     * Creates a building plan from its RL, replaces the constructor. Returns null if the file could not be loaded.
     * @param schematicPath The structure NBT file path of the desired structure
     * @param resourceManager Resource manager
     * @return a new building plan.
     */
    public static @Nullable SchematicContent create(ResourceLocation schematicPath, ResourceManager resourceManager) {
        try (InputStream inputStream = resourceManager.open(schematicPath)) {
            CompoundTag tag = NbtIo.readCompressed(inputStream);
            SchematicContent schematic = new SchematicContent();
            schematic.readSchematic(BuiltInRegistries.BLOCK.asLookup(), tag);
            return schematic.withoutVoidBlocks();
        } catch (FileNotFoundException fileNotFoundException) {
            Ouat.LOG.error("Could not find a schematic file. It should be located here: {}", schematicPath);
            return null;
        } catch (Throwable throwable) {
            Ouat.LOG.error("Could not load a schematic file. Check the file: {}", schematicPath, throwable);
            return null;
        }
    }

    /**
     * Create an array from this SchematicContent, that contains one Schematic for each slice on the Z axis.
     * @param shape SliceBuildShape of the schematic, used to remove the useless top and bottom blocks.
     * @return The array with the instance of SchematicContent corresponding to the slices.
     */
    public SchematicContent[] asSliceArray(SliceBuildType.SliceBuildShape shape){
        SchematicContent[] sliceArray = new SchematicContent[this.size.getZ()];
        int[] minY = new int[this.size.getZ()];
        int[] maxY = new int[this.size.getZ()];
        for (int z = 0; z < this.size.getZ(); z++) {
            SchematicContent temp = new SchematicContent();
            temp.size = new Vec3i(this.size.getX(), this.size.getY(), 1);
            sliceArray[z] = temp;
            minY[z] = shape.getMinYForSliceSchematic(z);
            maxY[z] = shape.getMaxYForSliceSchematic(z, this.size.getZ(), this.size.getY());
        }
        for(BlockInfo info : this.blocks){
            int patternIndex = info.pos().getZ();
            if(info.pos().getY() >= minY[patternIndex] && info.pos().getY() <= maxY[patternIndex]){
                sliceArray[patternIndex].blocks.add(info.move(0, -minY[patternIndex], -patternIndex));
            }
        }
        for(EntityInfo info : this.entities){
            int patternIndex = info.pos().getZ();
            if(info.pos().getY() >= minY[patternIndex] && info.pos().getY() <= maxY[patternIndex]){
                sliceArray[patternIndex].entities.add(info.move(0, -minY[patternIndex], -patternIndex));
            }
        }
        return sliceArray;
    }

    /**
     * Get the build slices of the build in parameter, and merge them to create a BuildSchematic.
     * The slices are moved to the correct Y and rotated in the build's direction.
     * @param build Slice build.
     * @param resourceManager Used to load the schematics.
     * @return The final SchematicContent rotated in the correct direction.
     */
    public static SchematicContent reconstruct(SliceBuild build, ResourceManager resourceManager){
        // First, we load the needed schematics.
        HashMap<String, SchematicContent[]> sliceMap = new HashMap<>();
        build.getBuildVariantMap().forEach((variantName, pair) -> {
            SchematicContent schematicContent = SchematicContent.create(pair.getA().getSchematicResource(build.getLevel()), resourceManager);
            if(schematicContent != null){
                sliceMap.put(variantName, schematicContent.asSliceArray(pair.getB()));
            };
        });
        // Now we can build the schematic using the slices.
        BlockPos originPos = build.getOriginPos();
        int originY = originPos.getY();
        int patternLength = ((SliceBuildType) build.getBuildType()).getPatternLength();
        SliceBuild.SliceProperty[] yShape = build.getYShape();
        SchematicContent schematic = new SchematicContent();
        for(int yIndex = 0; yIndex < yShape.length; yIndex++){
            SliceBuild.SliceProperty slice = yShape[yIndex];
            int offsetY = slice.y() - originY;
            List<BlockInfo> blocks = sliceMap.get(slice.variantName())[yIndex % patternLength].getBlocks();
            if(slice.shape() == SliceBuildType.SliceBuildShape.STAIRS_INVERTED){
                blocks = blocks.stream().map(BlockInfo::inverse).toList();
            }
            int finalYIndex = yIndex;
            schematic.blocks.addAll(blocks.stream().map(blockInfo -> blockInfo.move(0, offsetY, finalYIndex)).toList());
            List<EntityInfo> entities = sliceMap.get(slice.variantName())[yIndex % patternLength].getEntities();
            schematic.entities.addAll(entities.stream().map(entityInfo -> entityInfo.move(0, offsetY, finalYIndex)).toList());
        }
        schematic.size = new Vec3i(build.getNorthSizeX(), build.getSizeY(), yShape.length);
        return schematic;
    }

    public SchematicContent rotate(Direction direction){
        this.blocks.replaceAll(blockInfo -> blockInfo.rotateInBuild(direction, this.size.getX(), this.size.getZ()));
        this.entities.replaceAll(entityInfo -> entityInfo.rotate(direction, this.size.getX(), this.size.getZ()));
        if(direction.getAxis() == Direction.Axis.X){
            this.size = new Vec3i(this.size.getZ(), this.size.getY(), this.size.getX());
        }
        return this;
    }

    /**
     * Read the structure NBT file, initialize building plan blocks and entities
     * @param structureTag The structure NBT tag
     */
    private void readSchematic(HolderGetter<Block> blockGetter, CompoundTag structureTag) {
        // Extracting dimensions
        ListTag sizeTag = structureTag.getList("size", 3);
        this.size = new Vec3i(sizeTag.getInt(0), sizeTag.getInt(1), sizeTag.getInt(2));
        // Extracting entities
        ListTag entitiesTag = structureTag.getList("entities", 10);
        for(int i = 0; i < entitiesTag.size(); ++i) {
            CompoundTag entityTag = entitiesTag.getCompound(i);
            ListTag posTag = entityTag.getList("vec3", 6);
            Vec3 pos = new Vec3(posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));
            ListTag blockPosTag = entityTag.getList("pos", 3);
            BlockPos blockPos = new BlockPos(blockPosTag.getInt(0), blockPosTag.getInt(1), blockPosTag.getInt(2));
            if (entityTag.contains("entityNbt")) {
                CompoundTag entityNBT = entityTag.getCompound("entityNbt");
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
        this.buildBlocksList(blockGetter, paletteTag, blocksTag);
    }

    /**
     * Initialize the blocks list
     * @param paletteTag The structure blocks palette
     * @param blocksTag The structure blocks tag
     */
    private void buildBlocksList(HolderGetter<Block> blockGetter, ListTag paletteTag, ListTag blocksTag) {
        BuildSchematic.Palette palette = new BuildSchematic.Palette();
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
            if (blockTag.contains("entityNbt")) {
                blockNBT = blockTag.getCompound("entityNbt");
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
     * @return This building plan without void blocks
     */
    public SchematicContent withoutVoidBlocks() {
        List<BlockInfo> toRemove = new ArrayList<>();
        for (BlockInfo blockInfo : this.blocks) {
            if (blockInfo.state().getBlock() == Blocks.STRUCTURE_VOID) {
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

    public Vec3i getSize() {
        return size;
    }
}
