package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
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

/**
 * A SliceBuild is a series of 'subschematics' that connect to each other in order for the structure to adapt to the terrain. <br>
 * For example, a Road can go up, down or be flat locally, depending on the terrain. Each case has its schematic (stairs, slab, flat path...)
 */
public abstract class SliceBuild extends NpcBuild {
    private final int width;
    protected int length;
    protected SliceProperty[] yShape;

    public SliceBuild(SliceBuildType build, int length, int level) {
        super(build, level);
        this.width = build.getWidth();
        this.length = length;
        this.yShape = new SliceProperty[length];
    }

    protected SliceBuild(Culture culture, CompoundTag tag) {
        super(culture, tag);
        width = tag.getInt("Width");
        length = tag.getInt("Length");
        ListTag tags = tag.getList("Slices", ListTag.TAG_COMPOUND);
        yShape = new SliceProperty[tags.size()];
        for (int i = 0; i < yShape.length; i++) {
            if (tags.get(i) instanceof CompoundTag sliceCompoundTag) {
                yShape[i] = SliceProperty.readNBT(sliceCompoundTag);
            }
        }
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = super.save();
        tag.putInt("Width", width);
        tag.putInt("Length", length);
        ListTag tags = new ListTag();
        for (SliceBuild.SliceProperty slice : yShape) {
            tags.add(slice.writeNBT());
        }
        tag.put("Slices", tags);
        return tag;
    }

    @Override
    public boolean canBeBuiltOnBud(ProtoTown town, BuildBud buildBud, Direction dir) {
        // In the case of Roads, we only checks the line of block. The Map size will be defined when it's placed on the Map.
        BlockPos testedOriginPos = buildBud.findOriginPos(this, dir);
        BlockPos cursor = testedOriginPos.mutable();
        // We check all the position from the Bud to the width.
        for (int offset = 0; offset < width; offset++) {
            if (!town.isEmpty(cursor)) {
                return false;
            }
            cursor.relative(dir);
        }
        return true;
    }

    public SliceProperty[] getYShape() {
        return this.yShape;
    }

    /**
     * Function that compute the Y index of the given position in the YShape.
     * Be careful ! This value can be out of yShape bounds !
     * @param pos BlockPos to convert in YIndex.
     * @return the integer that correspond to the y index.
     */
    public int getYIndexFromPos(BlockPos pos){
        if (this.getDirection().getAxis() == Direction.Axis.X){
            return Math.abs(pos.getX() - this.getOriginPos().getX()) + 1;
        }else{
            return Math.abs(pos.getZ() - this.getOriginPos().getZ()) + 1;
        }
    }

    public void computeShape(ProtoTown town) {
        Direction dir = this.getDirection();
        // We set the cursors on both side of the Road at its start, on block before to smooth the curve.
        BlockPos.MutableBlockPos roadRightCursor = this.getCornerPos(TownMapUtils.Corner.getCornerNextToDir(dir.getOpposite(), false)).mutable().move(dir);
        BlockPos.MutableBlockPos roadLeftCursor = roadRightCursor.relative(dir.getClockWise(), this.getSize(dir) - 1).mutable();
        // The array has 2 more values on both sides to allow smoothing.
        int[] yArray = new int[this.length + 4];
        for (int i = 0; i < this.length + 2; i++) {
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
        for (int z = 0; z < this.length; z++) {
            if (this.yShape[z] != null && this.yShape[z].shape().isLocked()) {
                newYShape[z] = this.yShape[z];
                if (start < z) {
                    SliceBuild.SliceProperty[] smoothShape = this.smoothSliceSection(start + 2, z + 2, yArray);
                    System.arraycopy(smoothShape, 0, newYShape, start, smoothShape.length);
                }
                start = z + 1;
            }
        }
        if (start < this.length) {
            SliceBuild.SliceProperty[] smoothShape = this.smoothSliceSection(start + 2, this.length + 2, yArray);
            System.arraycopy(smoothShape, 0, newYShape, start, smoothShape.length);
        }
        this.yShape = newYShape;
    }

    /**
     * Computes the shapes of the roads.
     *
     * @param start  First slice we want to evaluate.
     * @param end    Last excluded slice that bound the studied slices.
     * @param yArray The array that contains all the Y values.
     * @return An array that contains the state of each slice.
     */
    private SliceProperty[] smoothSliceSection(int start, int end, int[] yArray) {
        int finalY = yArray[end];
        for (int i = start; i < end; i++) {
            // Make sure that the adjacent slice have max 1 Y of difference.
            if (yArray[i] < yArray[i - 1]) {
                yArray[i] = yArray[i - 1] - 1;
            } else if (yArray[i] > yArray[i - 1]) {
                yArray[i] = yArray[i - 1] + 1;
            }
            // Make sure the angle is not going further than 45 degrees.
            if (yArray[i] < finalY - (end - i)) {
                yArray[i] = yArray[i - 1] + 1;
            } else if (yArray[i] > finalY + (end - i)) {
                yArray[i] = yArray[i - 1] - 1;
            }
        }
        // Smoothing.
        float[] yFloats = new float[end - start + 2];
        yFloats[0] = yArray[start - 1];
        yFloats[yFloats.length - 1] = yArray[end];
        for (int i = start; i < end; i++) {
            yFloats[i - start + 1] = (yArray[i - 2] + yArray[i - 1] + yArray[i] + yArray[i + 1] + yArray[i + 2]) / 5.0F;
        }
        // Finally we build the slice array.
        SliceProperty[] slices = new SliceProperty[end - start];
        for (int i = 0; i < slices.length; i++) {
            float decimal = yFloats[i + 1] - (float) Math.floor(yFloats[i + 1]);
            int newY = (int) Math.ceil(yFloats[i + 1]);
            if ((int) Math.ceil(yFloats[i]) < newY && newY < (int) Math.ceil(yFloats[i + 2])) {
                slices[i] = new SliceProperty(newY, STAIRS, ((SliceBuildType) this.getBuildType()).getRandomVariantName(STAIRS));
            } else if ((int) Math.ceil(yFloats[i]) > newY && newY > (int) Math.ceil(yFloats[i + 2])) {
                slices[i] = new SliceProperty(newY, STAIRS_INVERTED, ((SliceBuildType) this.getBuildType()).getRandomVariantName(STAIRS));
            } else if (decimal > 0.5F || decimal == 0.0F) {
                slices[i] = new SliceProperty(newY, FLAT, ((SliceBuildType) this.getBuildType()).getRandomVariantName(FLAT));
            } else {
                slices[i] = new SliceProperty(newY, SLAB, ((SliceBuildType) this.getBuildType()).getRandomVariantName(SLAB));
            }
        }
        return slices;
    }

    @Override
    public StructurePiece createStructurePiece(Culture culture, @Nullable ProtoTown town) {
        return new SliceBuildPiece(culture.getId(), this, town);
    }

    public HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> getBuildVariantMap() {
        HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> map = new HashMap<>();
        for (SliceProperty sliceProperty : yShape) {
            String variantName = sliceProperty.buildVariantId();
            if (!map.containsKey(variantName)) {
                map.put(variantName, new Pair<>(((SliceBuildType) this.getBuildType()).getVariant(sliceProperty.shape(), variantName), sliceProperty.shape()));
            }
        }
        return map;
    }

    @Override
    public SchematicContent getSchematicContent(ResourceManager resourceManager) {
        return SchematicContent.getSliceBuildSchematic(this, resourceManager).rotate(this.getDirection());
    }

    @Override
    public int getNorthSizeX() {
        return width;
    }

    @Override
    public int getNorthSizeZ() {
        return length;
    }

    @Override
    public int getSizeY() {
        int pattern = ((SliceBuildType) this.getBuildType()).getPatternLength();
        HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> map = this.getBuildVariantMap();
        int minY = yShape[0].y();
        int maxY = minY;
        // We now compute the lowest Y and highest Y for each slice.
        for (SliceBuild.SliceProperty slice : yShape) {
            if (minY > slice.y()) {
                minY = slice.y();
            }
            int sliceTopY = slice.y() + slice.shape().getYSize(pattern, map.get(slice.buildVariantId()).getA().getDimensions().getY());
            if (sliceTopY > maxY) {
                maxY = sliceTopY;
            }
        }
        return maxY - minY;
    }

    public record SliceProperty(int y, SliceBuildType.SliceBuildShape shape, String buildVariantId) {
        public static SliceProperty readNBT(CompoundTag tag) {
            String variantName = tag.getString("VariantName");
            return new SliceProperty(
                    tag.getInt("Y"),
                    SliceBuildType.SliceBuildShape.valueOf(tag.getString("Shape")),
                    variantName
            );
        }

        public CompoundTag writeNBT() {
            CompoundTag sliceTag = new CompoundTag();
            sliceTag.putInt("Y", this.y);
            sliceTag.putString("Shape", this.shape.toString());
            sliceTag.putString("VariantName", this.buildVariantId);
            return sliceTag;
        }
    }
}
