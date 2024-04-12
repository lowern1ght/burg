package com.dotteam.onceuponatown.town.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.dotteam.onceuponatown.town.map.TownMapUtils.Corner;

import javax.annotation.Nullable;

import static com.dotteam.onceuponatown.town.map.TownMapUtils.rectangularPosIterator;

public abstract class MapBuild {
    private final int sizeXNorth;
    private int sizeZNorth;
    private BlockPos originPos;
    private int id;
    private Direction direction;
    public MapBuild(int sizeXNorth, int sizeZNorth){
        this.sizeXNorth = sizeXNorth;
        this.sizeZNorth = sizeZNorth;
    }

    /**
     * @return The id of the Build. 0 if the Build is not yet on the TownMap.
     */
    public int getId(){
        return this.id;
    }

    /**
     * @return The BlockPos of the NORTH_WEST corner of this MapBuild (its origin).
     */
    public BlockPos getOriginPos(){
        return this.originPos;
    }

    /**
     * @return The BlockPos of the given corner of this MapBuild.
     */
    public BlockPos getCornerPos(Corner corner){
        return Corner.NORTH_WEST.getCornerPos(this.originPos, this, this.direction, corner);
    }

    /**
     * @return The Direction of the MapBuild. Null if the MapBuild is not on the TownMap yet.
     */
    @Nullable
    public Direction getDirection(){
        return this.direction;
    }

    /**
     * @param originPos BlockPos that we want to test in order to find the correct Y coordinate.
     * @param dir Direction of this MapBuild we are testing.
     * @return The Y coordinate adapted to this build and the given BlockPos.
     */
    public int findAdaptedY(BlockPos originPos, Direction dir){
        return originPos.getY();
    }

    /**
     * @param originPos North-West corner of the MapBuild.
     * @param testedPos BlockPos studied within this MapBuild.
     * @return Function used to get the Y value of this MapBuild on a given position. By default, returns the Y value of
     * the position being checked.
     */
    public int getYOnPos(@Nullable BlockPos originPos, BlockPos testedPos) {
        return testedPos.getY();
    }

    /**
     * @param dir Direction in which we want the size of this MapBuild.
     * @return The width of the side of this MapBuild facing the given Direction.
     */
    public int getSize(@Nullable Direction dir){
        if(dir == null){
            dir = Direction.NORTH;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.getSizeX() : this.getSizeZ();
    }

    /**
     * @param dir Direction of the MapBuild. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this MapBuild on the X Axis.
     */
    public int getSizeX(@Nullable Direction dir){
        if(dir == null){
            return this.sizeXNorth;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.sizeXNorth : this.sizeZNorth;
    }

    /**
     * @return The current size of this MapBuild on the Axis X based on its direction.
     */
    public int getSizeX(){
        return this.getSizeX(this.getDirection());
    }

    /**
     * @param dir Direction of the MapBuild. If null, returns the size corresponding to the direction North.
     * @return the size of the side of this MapBuild on the Z Axis.
     */
    public int getSizeZ(@Nullable Direction dir) {
        if(dir == null){
            return this.sizeZNorth;
        }
        return dir.getAxis() == Direction.Axis.Z ? this.sizeZNorth : this.sizeXNorth;
    }

    /**
     * @return The current size of this MapBuild on the Axis Z based on its direction.
     */
    public int getSizeZ() {
        return this.getSizeZ(this.getDirection());
    }

    /**
     * Function called to add this MapBuild to the given TownMap, knowing that it can be added with the given parameters.
     * @param map TownMap in which this MapBuild will be added.
     * @param bud Bud used to put set this Building on the TownMap.
     * @param dir Direction corresponding to the orientation of this MapBuild.
     */
    public void addToMap(TownMap map, Bud bud, Direction dir){
        this.originPos = bud.findOriginPos(this, dir);
        this.direction = dir;
        this.id = map.generateNewID();
        map.addNewBuilds(this.id, this);
        this.onAddedToMap(map);
    }

    /**
     * Replace the NW Corner BlockPos with the given position. Used when the MapBuild must be extended.
     * @param newOrigin BlockPos from which this MapBuild now starts.
     */
    public void setOriginPos(BlockPos newOrigin){
        this.originPos = newOrigin;
    }

    /**
     * Extends the Z size for North direction by the given extensionSizeZ.
     * @param extensionSizeZ New size on Z axis.
     */
    protected void extendSizeZNorth(int extensionSizeZ){
        this.sizeZNorth += extensionSizeZ;
    }

    /**
     * Function called just after this MapBuild was added to the TownMap.
     * Override it to add post placement steps, like Buds generation.
     * @param map TownMap in which we add the Build.
     */
    protected abstract void onAddedToMap(TownMap map);

    /**
     * Check whenever the given MapBuild can be placed on the given Bud. I.e., we will test if the map is empty or if the
     * terrain is flat enough.
     * @param map TownMap where we are trying to build the MapBuild.
     * @param bud Bud that we are testing with the given direction.
     * @param dir Direction of the MapPath to which the MapBuild will be connected. The Y position of the MapBuild will correspond
     *            to the Y value of this MapPath at this MapBuild's DoorPoint.
     * @return True if the surface is indeed empty, false otherwise.
     */
    public boolean canBeBuiltOnBud(TownMap map, Bud bud, Direction dir){
        BlockPos testedOriginPos = bud.findOriginPos(this, dir);
        for(BlockPos.MutableBlockPos testedPos : rectangularPosIterator(testedOriginPos, this.getSizeX(dir), this.getSizeZ(dir))) {
            if(!map.isEmpty(testedPos)){
                return false;
            }
        }
        return true;
    }
}
