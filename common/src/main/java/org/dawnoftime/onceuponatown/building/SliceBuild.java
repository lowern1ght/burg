package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.structure.pieces.SliceBuildPiece;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.jetbrains.annotations.Nullable;
import oshi.util.tuples.Pair;

import java.util.HashMap;

public class SliceBuild<T extends SliceBuildType> extends Build<T> {
    private int width;
    private int length;
    private SliceProperty[] yShape;

    public SliceBuild(T build, int length) {
        super(build);
        this.width = build.getWidth();
        this.length = length;
        this.yShape = new SliceProperty[length];
    }

    public SliceBuild(Culture culture, Class<T> clazz, CompoundTag tag){
        super(culture, clazz, tag);
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
            //TODO Replace with the real Y Map query function.
            if(!map.isEmpty(cursor)){// || (Math.abs(TownMapDisplay.getSurfaceY(cursor)) - this.getYOnPos(testedOriginPos, cursor)) > MAXI_Y_DIFFERENCE){
                return false;
            }
            cursor.relative(dir);
        }
        return true;
    }


    @Override
    public StructurePiece generatePieces(StructureTemplateManager manager, Culture culture, @Nullable ProtoTown town) {
        return new SliceBuildPiece(this.getOriginPos(), this.getDirection(), this, culture, town);
    }

    public SliceProperty[] getYShape() {
        return this.yShape;
    }

    public HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> getBuildVariantMap(){
        HashMap<String, Pair<BuildVariant, SliceBuildType.SliceBuildShape>> map = new HashMap<>();
        for (SliceProperty sliceProperty : this.yShape) {
            String variantName = sliceProperty.variantName();
            if (!map.containsKey(variantName)) {
                map.put(variantName, new Pair<>(this.getBuildType().getVariant(sliceProperty.shape(), variantName), sliceProperty.shape()));
            }
        }
        return map;
    }

    public int getYSize(){
        int pattern = this.getBuildType().getPatternLength();
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
            return new SliceProperty(tag.getInt("Y"), SliceBuildType.SliceBuildShape.fromString("???", variantName, tag.getString("Shape")), variantName);
        }

        public CompoundTag writeNBT(){
            CompoundTag sliceTag = new CompoundTag();
            sliceTag.putInt("Y", this.y);
            sliceTag.putString("Shape", this.shape.getShapeName());
            sliceTag.putString("VariantName", this.variantName);
            return sliceTag;
        }
    }
}
