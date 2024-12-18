package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.schematic.SchematicContent;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.structure.pieces.SliceBuildPiece;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.util.HashMap;

import static org.dawnoftime.onceuponatown.building.type.SliceBuildType.SliceBuildShape.*;

public abstract class SliceBuild extends Build {
    private final int width;
    protected int length;
    protected SliceProperty[] yShape;

    public SliceBuild(SliceBuildType build, int length) {
        super(build);
        this.width = build.getWidth();
        this.length = length;
        this.yShape = new SliceProperty[length];
    }

    public SliceBuild(Culture culture, CompoundTag tag){
        super(culture, tag);
        this.width = tag.getInt("Width");
        this.length = tag.getInt("Length");
        ListTag tags = tag.getList("Slices", ListTag.TAG_COMPOUND);
        this.yShape = new SliceProperty[tags.size()];
        for (int i = 0; i < this.yShape.length; i++) {
            if(tags.get(i) instanceof CompoundTag sliceCompoundTag){
                this.yShape[i] = SliceProperty.readNBT(sliceCompoundTag);
            }
        }
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putInt("Width", this.width);
        tag.putInt("Length", this.length);
        ListTag tags = new ListTag();
        for(SliceBuild.SliceProperty slice : this.yShape){
            tags.add(slice.writeNBT());
        }
        tag.put("Slices", tags);
        return tag;
    }

    @Override
    public SchematicContent getSchematicContent(ResourceManager resourceManager) {
        return SchematicContent.reconstruct(this, resourceManager).rotate(this.getDirection());
    }

    @Override
    public int getNorthSizeX() {
        return this.width;
    }

    @Override
    public int getNorthSizeZ() {
        return this.length;
    }

    @Override
    public boolean canBeBuiltOnBud(ProtoTown map, BuildBud buildBud, Direction dir) {
        // In the case of MapPaths, we only checks the line of block. The Map size will be defined when it's placed on the Map.
        BlockPos testedOriginPos = buildBud.findOriginPos(this, dir);
        BlockPos cursor = testedOriginPos.mutable();
        // We check all the position from the Bud to the width.
        for(int offset = 0; offset < this.width; offset++){
            if(!map.isEmpty(cursor)){
                return false;
            }
            cursor.relative(dir);
        }
        return true;
    }

    public SliceProperty[] getYShape() {
        return this.yShape;
    }

    public void computeShape(ProtoTown town){
        Direction dir = this.getDirection();
        // We set the cursors on both side of the road at its start, on block before to smooth the curve.
        BlockPos.MutableBlockPos roadRightCursor = this.getCornerPos(TownMapUtils.Corner.getCornerNextToDir(dir.getOpposite(), false)).mutable().move(dir);
        BlockPos.MutableBlockPos roadLeftCursor = roadRightCursor.relative(dir.getClockWise(), this.getSize(dir) - 1).mutable();
        // The array has 2 more values on both sides to allow smoothing.
        int[] yArray = new int[this.length + 4];
        for(int i = 0; i < this.length + 2; i++){
            yArray[i + 1] = (town.getSurfaceY(roadRightCursor.getX(), roadLeftCursor.getZ()) + town.getSurfaceY(roadRightCursor.getX(), roadLeftCursor.getZ())) / 2;
            roadRightCursor.move(dir.getOpposite());
            roadLeftCursor.move(dir.getOpposite());
        }
        // We fill the first and last y with the adjacent one. These values will be used to smooth the shape later.
        yArray[0] = yArray[1];
        yArray[yArray.length - 1] = yArray[yArray.length - 2];
        // Finally we create the new shape. To do that, we iterate on the Y and smooth section by section (delimited by locked slices).
        SliceBuild.SliceProperty[] newYShape = new SliceProperty[this.length];
        int start = 0;
        for(int z = 0; z < this.length; z++){
            if(this.yShape[z] != null && this.yShape[z].shape().isLocked()){
                newYShape[z] = this.yShape[z];
                if(start < z){
                    SliceBuild.SliceProperty[] smoothShape = this.smoothSliceSection(start + 2, z + 2, yArray);
                    System.arraycopy(smoothShape, 0, newYShape, start, smoothShape.length);
                }
                start = z + 1;
            }
        }
        if(start < this.length){
            SliceBuild.SliceProperty[] smoothShape = this.smoothSliceSection(start + 2, this.length + 2, yArray);
            System.arraycopy(smoothShape, 0, newYShape, start, smoothShape.length);
        }
        this.yShape = newYShape;
    }

    /**
     * Function that compute the shapes of the roads.
     * @param start First slice we want to evaluate.
     * @param end Last excluded slice that bound the studied slices.
     * @param yArray The array that contains all the Y values.
     * @return An array that contains the state of each slice.
     */
    private SliceProperty[] smoothSliceSection(int start, int end, int[] yArray){
        int finalY = yArray[end];
        for(int i = start; i < end; i++){
            // Make sure that the adjacent slice have max 1 Y of difference.
            if(yArray[i] < yArray[i - 1]){
                yArray[i] = yArray[i - 1] - 1;
            }else if(yArray[i] > yArray[i - 1]) {
                yArray[i] = yArray[i - 1] + 1;
            }
            // Make sure the angle is not going further than 45 degrees.
            if(yArray[i] < finalY - (end - i)){
                yArray[i] = yArray[i - 1] + 1;
            }else if(yArray[i] > finalY + (end - i)){
                yArray[i] = yArray[i - 1] - 1;
            }
        }
        // Smoothing.
        float[] yFloats = new float[end - start + 2];
        yFloats[0] = yArray[start - 1];
        yFloats[yFloats.length - 1] = yArray[end];
        for(int i = start; i < end; i++){
            yFloats[i - start + 1] = (yArray[i - 2] + yArray[i - 1] + yArray[i] + yArray[i + 1] + yArray[i + 2]) / 5.0F;
        }
        // Finally we build the slice array.
        SliceProperty[] slices = new SliceProperty[end - start];
        for(int i = 0; i < slices.length; i++){
            float decimal = yFloats[i + 1] - (float) Math.floor(yFloats[i + 1]);
            int newY = (int) Math.ceil(yFloats[i + 1]);
            if((int) Math.ceil(yFloats[i]) < newY && newY < (int) Math.ceil(yFloats[i + 2])){
                slices[i] = new SliceProperty(newY, STAIRS, ((SliceBuildType) this.getBuildType()).getRandomVariantName(STAIRS));
            }else if((int) Math.ceil(yFloats[i]) > newY && newY > (int) Math.ceil(yFloats[i + 2])){
                slices[i] = new SliceProperty(newY, STAIRS_INVERTED, ((SliceBuildType) this.getBuildType()).getRandomVariantName(STAIRS));
            }else if(decimal > 0.5F || decimal == 0.0F){
                slices[i] = new SliceProperty(newY, FLAT, ((SliceBuildType) this.getBuildType()).getRandomVariantName(FLAT));
            }else{
                slices[i] = new SliceProperty(newY, SLAB, ((SliceBuildType) this.getBuildType()).getRandomVariantName(SLAB));
            }
        }
        return slices;
    }

    @Override
    public StructurePiece generatePieces(Culture culture, @Nullable ProtoTown town) {
        return new SliceBuildPiece(culture.getId(), this, town);
    }

    public HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> getBuildVariantMap(){
        HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> map = new HashMap<>();
        for (SliceProperty sliceProperty : this.yShape) {
            String variantName = sliceProperty.variantName();
            if (!map.containsKey(variantName)) {
                map.put(variantName, new Pair<>(((SliceBuildType) this.getBuildType()).getVariant(sliceProperty.shape(), variantName), sliceProperty.shape()));
            }
        }
        return map;
    }

    @Override
    public int getSizeY(){
        int pattern = ((SliceBuildType) this.getBuildType()).getPatternLength();
        HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> map = this.getBuildVariantMap();
        int minY = this.yShape[0].y();
        int maxY = minY;
        // We now compute the lowest Y and highest Y for each slice.
        for(SliceBuild.SliceProperty slice : this.yShape){
            if(minY > slice.y()){
                minY = slice.y();
            }
            int sliceTopY = slice.y() + slice.shape().getYSize(pattern, map.get(slice.variantName()).getA().getSize().getY());
            if(sliceTopY > maxY){
                maxY = sliceTopY;
            }
        }
        return maxY - minY;
    }

    public record SliceProperty(int y, SliceBuildType.SliceBuildShape shape, String variantName){
        public static SliceProperty readNBT(CompoundTag tag){
            String variantName = tag.getString("VariantName");
            return new SliceProperty(
                    tag.getInt("Y"),
                    SliceBuildType.SliceBuildShape.valueOf(tag.getString("Shape")),
                    variantName
            );
        }

        public CompoundTag writeNBT(){
            CompoundTag sliceTag = new CompoundTag();
            sliceTag.putInt("Y", this.y);
            sliceTag.putString("Shape", this.shape.toString());
            sliceTag.putString("VariantName", this.variantName);
            return sliceTag;
        }
    }
}
