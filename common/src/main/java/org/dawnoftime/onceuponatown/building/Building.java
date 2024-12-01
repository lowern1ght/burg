package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.dawnoftime.onceuponatown.building.schematic.BuildVariant;
import org.dawnoftime.onceuponatown.building.type.BuildingType;
import org.dawnoftime.onceuponatown.structure.pieces.BuildingPiece;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils.Corner;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;

import static org.dawnoftime.onceuponatown.town.generation.TownMapUtils.MAXI_Y_DIFFERENCE;
import static org.dawnoftime.onceuponatown.town.generation.TownMapUtils.rectangularPosIterator;

public class Building<T extends BuildingType> extends Build<T> {
    private final BuildVariant variant;

    public Building(T type, BuildVariant variant) {
        super(type);
        this.variant = variant;
    }

    @Override
    public int getNorthSizeX() {
        return this.variant.getSize().getX();
    }

    @Override
    public int getNorthSizeZ() {
        return this.variant.getSize().getZ();
    }

    @Override
    public void generatePieces(StructurePiecesBuilder builder, StructureTemplateManager manager, @Nullable ProtoTown town) {
        BuildingPiece piece;
        if(town == null){
            piece = new BuildingPiece(manager, this.variant.getSchematicResource(this.getLevel()), this.getCornerPos(corner), rotation);
        }else{
            piece = new DataSliceBuildPiece(manager, buildingName, building.getCornerPos(corner), rotation, town);
        }
        builder.addPiece(piece);
    }

    @Override
    protected void onAddedToMap(ProtoTown town) {
        // We try to find all the adjacent MapPath to extend them and add the Buds.
        // We will iterate on a one block bigger rectangle to find all the adjacent MapBuild.
        HashSet<Integer> ids = new HashSet<>();
        for(BlockPos.MutableBlockPos pos : rectangularPosIterator(this.getOriginPos().north().west(), this.getSizeX() + 2, this.getSizeZ() + 2)) {
            ids.add(town.getMapBlockInMapPos(pos));
        }
        for(int id : ids){
            if(town.getBuild(id) instanceof RoadBuild path){
                path.update(town);
            }
        }
    }

    /**
     * @param originPos North-West BlockPos of the building.
     * @param dir Direction of the building.
     * @return The BlockPos of the door of this MapBuilding, with the given parameters.
     */
    private BlockPos getDoorYPos(BlockPos originPos, Direction dir){
        //TODO Replace this function with the real position of the Door.

        //TODO Fix this function, it doesn't seem to work properly
        // I will assume the door is in the middle of the North side.
        int offset = dir.getAxis() == Direction.Axis.X ? this.getSizeZ(dir) : this.getSizeX(dir);
        return Corner.NORTH_WEST.getCornerPos(originPos, this, dir, Corner.getCornerNextToDir(dir.getOpposite(), false)).relative(dir.getClockWise(), offset / 2);
    }

    @Override
    public int findAdaptedY(BlockPos originPos, Direction dir) {
        return this.getDoorYPos(originPos, dir).getY();
    }

    @Override
    public int getYOnPos(BlockPos originPos, BlockPos testedPos) {
        return originPos != null ? originPos.getY() : super.getYOnPos(null, testedPos);
    }

    @Override
    public boolean canBeBuiltOnBud(TownMap map, BuildBud buildBud, Direction dir) {
        BlockPos testedOriginPos = buildBud.findOriginPos(this, dir);
        for(BlockPos.MutableBlockPos testedPos : rectangularPosIterator(testedOriginPos, this.getSizeX(dir), this.getSizeZ(dir))) {
            if(!map.isEmpty(testedPos) || Math.abs(testedPos.getY() - this.getYOnPos(testedOriginPos, testedPos)) > MAXI_Y_DIFFERENCE){
                return false;
            }
        }
        return true;
    }
}
