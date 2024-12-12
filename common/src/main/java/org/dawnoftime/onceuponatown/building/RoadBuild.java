package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import java.util.ArrayList;
import java.util.Objects;

import static org.dawnoftime.onceuponatown.culture.Culture.WIDE_ROAD_TYPE_NAME;
import static org.dawnoftime.onceuponatown.town.generation.ProtoTown.RANDOM_SOURCE;
import static org.dawnoftime.onceuponatown.Config.DEFAULT_PATH_LENGTH;
import static org.dawnoftime.onceuponatown.Config.PATH_STOP_RATE;

public class RoadBuild extends SliceBuild {
    private final boolean isWide;
    private boolean canGrow = true;

    public RoadBuild(SliceBuildType build, int length) {
        super(build, length);
        this.isWide = Objects.equals(build.getName(), WIDE_ROAD_TYPE_NAME);
    }

    public RoadBuild(Culture culture, CompoundTag tag) {
        super(culture, tag);
        this.isWide = tag.getBoolean("IsWide");
        this.canGrow = tag.getBoolean("CanGrow");
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putBoolean("IsWide", this.isWide);
        tag.putBoolean("CanGrow", this.canGrow);
        return tag;
    }

    @Override
    public CompoundTag getDataForGui() {
        CompoundTag displayData = new CompoundTag();
        displayData.putString("BuildType", getBuildType().getName());
        displayData.putString("BuildCategory", getBuildTypeCategory().toString());
        displayData.put("OriginPos", NbtUtils.writeBlockPos(getOriginPos()));
        displayData.putInt("SizeX", getSizeX());
        displayData.putInt("SizeZ", getSizeZ());
        displayData.putInt("Level", getLevel());
        displayData.putBoolean("IsWide", isWide);
        displayData.putBoolean("CanGrow", canGrow);
        return displayData;
    }

    public boolean isWide() {
        return this.isWide;
    }

    @Override
    protected void onAddedToTown(ProtoTown map) {
        this.updateRoad(map);
    }

    /**
     * Function that tries to grow this MapPath, and adds the associated Buds.
     * @param town TownMap of this MapPath.
     */
    public void updateRoad(ProtoTown town){
        this.tryGrowing(town);
        this.findAllBuds(town);
        this.computeShape(town);
    }

    private void tryGrowing(ProtoTown town) {
        if(this.canGrow){
            // We decide if this Path will stop growing definitively after this growth.
            if(!this.isWide){
                if(RANDOM_SOURCE.nextFloat() < PATH_STOP_RATE){
                    this.canGrow = false;
                }
            }
            // If the road will stop growing, it should stop directly at the end of the adjacent Build.
            int bonusSize = this.canGrow ? DEFAULT_PATH_LENGTH : 0;
            int dirGrowth = this.getGrowthSize(town, this.getDirection(), bonusSize);
            int oppositeDirGrowth = this.getGrowthSize(town, this.getDirection().getOpposite(), bonusSize);
            if(dirGrowth + oppositeDirGrowth > 0){
                // We move the origin depending on the growth.
                if(this.getDirection() == Direction.NORTH || this.getDirection() == Direction.WEST){
                    if(dirGrowth > 0){
                        this.setOriginPos(this.getOriginPos().relative(this.getDirection(), dirGrowth));
                    }
                }else{
                    if(oppositeDirGrowth > 0){
                        this.setOriginPos(this.getOriginPos().relative(this.getDirection(), -oppositeDirGrowth));
                    }
                }
                // Finally we change the size which will also update the shape.
                this.extendLength(dirGrowth, oppositeDirGrowth);
            }
            // And lastly we will add the special Buds : bridge or stairs
            town.updateTownMap(this);
        }
    }

    /**
     * Extends the length of this road. The yShape needs to be updated afterward !
     * @param extendInDir Number of blocks extended in this build's direction.
     * @param extendInOppositeDir Number of blocks extended in this build's opposite direction.
     */
    protected void extendLength(int extendInDir, int extendInOppositeDir){
        this.length += extendInDir + extendInOppositeDir;
        // We move the YShape so that the locked shapes stay at the same position.
        SliceProperty[] newYShape = new SliceProperty[this.length];
        System.arraycopy(this.yShape, 0, newYShape, extendInOppositeDir, this.yShape.length);
        this.yShape = newYShape;
    }

    /**
     * @param map TownMap in which we try to extend the MapPath.
     * @param dir Direction in which we try to extend the MapPath.
     * @param bonusSize Extra size that the MapPath should have after the last adjacent MapBuild. This value is 0 if the MapPath
     *                  stops growing, thus it will stop definitively at the end of its adjacent MapBuild.
     * @return The total number of block this MapPath should grow in the given direction to reach the border of adjacent buildings
     * plus the DEFAULT_PATH_LENGTH. Returns 0 if this MapPath already stops at the correct position.
     */
    private int getGrowthSize(ProtoTown map, Direction dir, int bonusSize){
        int growth = -bonusSize;
        int emptyAdjacentBlocks = 0;
        BlockPos initPos = this.getCornerPos(TownMapUtils.Corner.getCornerNextToDir(dir.getOpposite(), false)).relative(dir, 1 - bonusSize);
        BlockPos.MutableBlockPos adjLeftCursor = initPos.relative(dir.getClockWise(), -1).mutable();
        BlockPos.MutableBlockPos pathLeftCursor = initPos.mutable();
        BlockPos.MutableBlockPos pathRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir) - 1).mutable();
        BlockPos.MutableBlockPos adjRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir)).mutable();
        while(true){
            // While the cursor is at an empty vec3 (or the vec3 contains this block), we can extend this MapPath.
            //TODO Check if there is a Y difference to big : we stop and will make a tower Bud.
            if(map.isEmpty(pathLeftCursor, this) && map.isEmpty(pathRightCursor, this)){
                growth++;
                emptyAdjacentBlocks++;
                if(!map.isEmpty(adjLeftCursor) || !map.isEmpty(adjRightCursor)){
                    // If one of the 2 current adjacent blocks are not empty, we reset the number of empty adjacent blocks.
                    emptyAdjacentBlocks = 0;
                }
                if(emptyAdjacentBlocks >= bonusSize){
                    // If the number of empty adjacent blocks has reached the minimal bonusSize, then the road stops growing.
                    return growth;
                }
                adjLeftCursor.move(dir);
                pathLeftCursor.move(dir);
                pathRightCursor.move(dir);
                adjRightCursor.move(dir);
            }else{
                return growth;
            }
        }
    }

    /**
     * Create the Buds on the sides of this MapPath. Some Buds can be added at the top and bottom only if it can not grow.
     * @param town TownMap in which we want to add the Buds.
     */
    private void findAllBuds(ProtoTown town){
        ArrayList<BuildBud> newBuildBuds = new ArrayList<>();
        if(this.getDirection().getAxis() == Direction.Axis.X){
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_WEST, this.getSizeX()));
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_EAST, this.getSizeX()));
            if(!this.canGrow){
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_EAST, this.getSizeZ()));
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_WEST, this.getSizeZ()));
            }
        }else{
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_EAST, this.getSizeZ()));
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_WEST, this.getSizeZ()));
            if(!this.canGrow){
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_WEST, this.getSizeX()));
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_EAST, this.getSizeX()));
            }
        }
        newBuildBuds.forEach(town::tryCreateRoad);
    }

    /**
     * Create Buds based on adjacent content in the TownMap, on a line starting clockwise from the cornerPos.
     * @param town TownMap in which we create the Buds.
     * @param corner Corner to start the exploration. The side studied is always the rightDirection from this corner (i.e.
     *              for NORTH_WEST, we will study the NORTH side of this MapPath).
     * @param sideLength Size of the size to study.
     * @return A list that contains the Buds created in this function.
     */
    private ArrayList<BuildBud> findBudsOnSide(ProtoTown town, TownMapUtils.Corner corner, int sideLength) {
        // We move the start BlockPos one block out of the MapBuild in diagonal.
        BlockPos cornerPos = this.getCornerPos(corner).relative(corner.getRightDirection()).relative(corner.getLeftDirection());
        BlockPos.MutableBlockPos cursor = cornerPos.mutable();
        boolean isPreviousPosEmpty = town.isEmpty(cursor);
        cursor.move(corner.getLeftDirection(), -1);
        boolean isCurrentPosEmpty;
        ArrayList<BuildBud> buildBuds = new ArrayList<>();
        // We create an array that contains whenever each BlockPos is empty or not.
        for(int i = 1; i < sideLength + 2; i++){
            isCurrentPosEmpty = town.isEmpty(cursor);
            // We modify the value for the start and the end of the loop.
            if(i == 1) {
                isPreviousPosEmpty &= isCurrentPosEmpty;
            }
            if(i == sideLength + 1){
                isCurrentPosEmpty &= isPreviousPosEmpty;
            }
            // Finally we create the Bud if needed.
            if(isCurrentPosEmpty != isPreviousPosEmpty){
                buildBuds.add(town.addToBuds(this.setupBud(town, cursor.immutable(), isCurrentPosEmpty, corner.getRightDirection())));
            }
            isPreviousPosEmpty = isCurrentPosEmpty;
            cursor.move(corner.getLeftDirection(), -1);
        }
        return buildBuds;
    }

    /**
     * Creates a Bud at the currentPos or position before depending on which one is empty.
     * @param town TownMap is which we want to create a Bud.
     * @param currentPos Current position of the cursor.
     * @param isCurrentPosEmpty True if the current position of the cursor is empty, false otherwise.
     * @param budDir Direction oriented at the opposite of the MapPath, that corresponds to the RightDir of the Corner.
     * @return The created Bud instance.
     */
    private BuildBud setupBud(ProtoTown town, BlockPos currentPos, boolean isCurrentPosEmpty, Direction budDir) {
        BlockPos previousPos = currentPos.relative(budDir.getCounterClockWise());
        Direction[] pathDirection = new Direction[town.getBuild(isCurrentPosEmpty ? previousPos : currentPos) instanceof SliceBuild ? 2 : 1];
        pathDirection[0] = budDir.getOpposite();
        if(pathDirection.length > 1){
            pathDirection[1] = isCurrentPosEmpty ? budDir.getCounterClockWise() : budDir.getClockWise();
        }
        return new BuildBud(BuildBud.BudType.DEFAULT, town, isCurrentPosEmpty ? currentPos.getX() : previousPos.getX(), isCurrentPosEmpty ? currentPos.getZ() : previousPos.getZ(), TownMapUtils.Corner.getCornerNextToDir(budDir, isCurrentPosEmpty), pathDirection);
    }

    @Override
    protected BuildCategory getBuildTypeCategory() {
        return BuildCategory.ROAD;
    }
}
