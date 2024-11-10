package org.dawnoftime.onceuponatown.building.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.town.TownMap;
import org.dawnoftime.onceuponatown.town.TownMapUtils.Corner;

import static org.dawnoftime.onceuponatown.town.TownMapUtils.MAXI_Y_DIFFERENCE;
import static org.dawnoftime.onceuponatown.town.TownMapUtils.rectangularPosIterator;

public class BuildingPlacement extends PlotPlacement {

    public BuildingPlacement(int sizeXNorth, int sizeZNorth) {
        super(sizeXNorth, sizeZNorth);
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
