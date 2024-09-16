package org.dawnoftime.onceuponatown.town.map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.town.map.TownMapUtils.Corner;
import java.util.ArrayList;

import static org.dawnoftime.onceuponatown.town.map.TownMapUtils.*;

public class MapRoad extends MapBuild{
    private final boolean isBig;
    private boolean canGrow = true;
    private int[] yShape;

    public MapRoad(int length, boolean isBig) {
        super(getWidth(isBig), length);
        this.isBig = isBig;
        //TODO handle the Y shape
        this.yShape = new int[length];
    }

    @Override
    protected void onAddedToMap(TownMap map) {
        this.update(map);
    }

    /**
     * Function that tries to grow this MapPath, and adds the associated Buds.
     * @param map TownMap of this MapPath.
     */
    public void update(TownMap map){
        this.tryGrowing(map);
        this.findAllBuds(map);
    }

    private void tryGrowing(TownMap map) {
        if(this.canGrow && this.getDirection() != null){
            // We decide if this Path will stop growing definitively after this growth.
            if(!this.isBig){
                if(map.randomSource.nextFloat() < PATH_STOP_RATE){
                    this.canGrow = false;
                }
            }
            int bonusSize = this.canGrow ? DEFAULT_PATH_LENGTH : 0;
            int dirGrowth = this.getGrowthSize(map, this.getDirection(), bonusSize);
            int oppositeDirGrowth = this.getGrowthSize(map, this.getDirection().getOpposite(), bonusSize);
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
                // Finally we change the sizes.
                this.extendSizeZNorth(dirGrowth + oppositeDirGrowth);
            }
            // And lastly we will add the special Buds : bridge or stairs
            map.updateTownMap(this);
        }
    }

    /**
     * @param map TownMap in which we try to extend the MapPath.
     * @param dir Direction in which we try to extend the MapPath.
     * @param bonusSize Extra size that the MapPath should have after the last adjacent MapBuild. This value is 0 if the MapPath
     *                  stops growing, thus it will stop definitively at the end of its adjacent MapBuild.
     * @return The total number of block this MapPath should grow in the given direction to reach the border of adjacent buildings
     * plus the DEFAULT_PATH_LENGTH. Returns 0 if this MapPath already stops at the correct position.
     */
    private int getGrowthSize(TownMap map, Direction dir, int bonusSize){
        if(this.getDirection() != null){
            int growth = -bonusSize;
            int emptyAdjacentBlocks = 0;
            BlockPos initPos = this.getCornerPos(TownMapUtils.Corner.getCornerNextToDir(dir.getOpposite(), false)).relative(dir, 1 - bonusSize);
            BlockPos.MutableBlockPos adjLeftCursor = initPos.relative(dir.getClockWise(), -1).mutable();
            BlockPos.MutableBlockPos pathLeftCursor = initPos.mutable();
            BlockPos.MutableBlockPos pathRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir) - 1).mutable();
            BlockPos.MutableBlockPos adjRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir)).mutable();
            while(true){
                // While the cursor is at an empty pos (or the pos contains this block), we can extend this MapPath.
                //TODO Check if there is a Y difference to big : we stop and will make a stairs Bud.
                if(map.isEmpty(pathLeftCursor, this.getId()) && map.isEmpty(pathRightCursor, this.getId())){
                    growth++;
                    emptyAdjacentBlocks++;
                    if(!map.isEmpty(adjLeftCursor) || !map.isEmpty(adjRightCursor)){
                        // If on of the 2 current adjacent blocks are not empty, we reset the number of empty adjacent blocks.
                        emptyAdjacentBlocks = 0;
                    }
                    if(emptyAdjacentBlocks >= bonusSize){
                        // If the number of empty adjacent blocks has reached the minimal bonusSize, then the MapPath can stop growing.
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
        return 0;
    }

    /**
     * Create the Buds on the sides of this MapPath. Some Buds can be added at the top and bottom only if it can not grow.
     * @param map TownMap in which we want to add the Buds.
     */
    private void findAllBuds(TownMap map){
        ArrayList<BuildBud> newBuildBuds = new ArrayList<>();
        if(this.getDirection() != null){
            if(this.getDirection().getAxis() == Direction.Axis.X){
                newBuildBuds.addAll(this.findBudsOnSide(map, Corner.NORTH_WEST, this.getSizeX()));
                newBuildBuds.addAll(this.findBudsOnSide(map, Corner.SOUTH_EAST, this.getSizeX()));
                if(!this.canGrow){
                    newBuildBuds.addAll(this.findBudsOnSide(map, Corner.NORTH_EAST, this.getSizeZ()));
                    newBuildBuds.addAll(this.findBudsOnSide(map, Corner.SOUTH_WEST, this.getSizeZ()));
                }
            }else{
                newBuildBuds.addAll(this.findBudsOnSide(map, Corner.NORTH_EAST, this.getSizeZ()));
                newBuildBuds.addAll(this.findBudsOnSide(map, Corner.SOUTH_WEST, this.getSizeZ()));
                if(!this.canGrow){
                    newBuildBuds.addAll(this.findBudsOnSide(map, Corner.NORTH_WEST, this.getSizeX()));
                    newBuildBuds.addAll(this.findBudsOnSide(map, Corner.SOUTH_EAST, this.getSizeX()));
                }
            }
            newBuildBuds.forEach(map::tryCreatePath);
        }else{
            throw new IllegalStateException("Unexpected creation of Buds: It's impossible to create Buds before the source MapPath is placed on the TownMap.");
        }
    }

    /**
     * Create Buds based on adjacent content in the TownMap, on a line starting clockwise from the cornerPos.
     * @param map TownMap in which we create the Buds.
     * @param corner Corner to start the exploration. The side studied is always the rightDirection from this corner (i.e.
     *              for NORTH_WEST, we will study the NORTH side of this MapPath).
     * @param sideLength Size of the size to study.
     * @return A list that contains the Buds created in this function.
     */
    private ArrayList<BuildBud> findBudsOnSide(TownMap map, Corner corner, int sideLength) {
        // We move the start BlockPos one block out of the MapBuild in diagonal.
        BlockPos cornerPos = this.getCornerPos(corner).relative(corner.getRightDirection()).relative(corner.getLeftDirection());
        BlockPos.MutableBlockPos cursor = cornerPos.mutable();
        boolean isPreviousPosEmpty = map.isEmpty(cursor);
        cursor.move(corner.getLeftDirection(), -1);
        boolean isCurrentPosEmpty;
        ArrayList<BuildBud> buildBuds = new ArrayList<>();
        // We create an array that contains whenever each BlockPos is empty or not.
        for(int i = 1; i < sideLength + 2; i++){
            isCurrentPosEmpty = map.isEmpty(cursor);
            // We modify the value for the start and the end of the loop.
            if(i == 1) {
                isPreviousPosEmpty &= isCurrentPosEmpty;
            }
            if(i == sideLength + 1){
                isCurrentPosEmpty &= isPreviousPosEmpty;
            }
            // Finally we create the Bud if needed.
            if(isCurrentPosEmpty != isPreviousPosEmpty){
                buildBuds.add(this.setupBud(map, cursor.immutable(), isCurrentPosEmpty, corner.getRightDirection()));
            }
            isPreviousPosEmpty = isCurrentPosEmpty;
            cursor.move(corner.getLeftDirection(), -1);
        }
        return buildBuds;
    }

    /**
     * Creates a Bud at the currentPos or position before depending on which one is empty.
     * @param map TownMap is which we want to create a Bud.
     * @param currentPos Current position of the cursor.
     * @param isCurrentPosEmpty True if the current position of the cursor is empty, false otherwise.
     * @param budDir Direction oriented at the opposite of the MapPath, that corresponds to the RightDir of the Corner.
     * @return The created Bud instance.
     */
    private BuildBud setupBud(TownMap map, BlockPos currentPos, boolean isCurrentPosEmpty, Direction budDir) {
        BlockPos previousPos = currentPos.relative(budDir.getCounterClockWise());
        Direction[] pathDirection = new Direction[map.getBuild(isCurrentPosEmpty ? previousPos : currentPos) instanceof MapRoad ? 2 : 1];
        pathDirection[0] = budDir.getOpposite();
        if(pathDirection.length > 1){
            pathDirection[1] = isCurrentPosEmpty ? budDir.getCounterClockWise() : budDir.getClockWise();
        }
        return BuildBud.createBud(map, isCurrentPosEmpty ? currentPos : previousPos, Corner.getCornerNextToDir(budDir, isCurrentPosEmpty), pathDirection);
    }

    @Override
    public boolean canBeBuiltOnBud(TownMap map, BuildBud buildBud, Direction dir) {
        // In the case of MapPaths, we only checks the line of block. The Map size will be defined when it's placed on the Map.
        BlockPos testedOriginPos = buildBud.findOriginPos(this, dir);
        BlockPos cursor = testedOriginPos.mutable();
        // We check all the position from the Bud to the width.
        for(int offset = 0; offset < getWidth(this.isBig()); offset++){
            //TODO Replace with the real Y Map query function.
            if(!map.isEmpty(cursor)){// || (Math.abs(TownMapDisplay.getSurfaceY(cursor)) - this.getYOnPos(testedOriginPos, cursor)) > MAXI_Y_DIFFERENCE){
                return false;
            }
            cursor.relative(dir);
        }
        return true;
    }

    /**
     * @return True if this MapPath is big, false otherwise.
     */
    public boolean isBig() {
        return this.isBig;
    }

    /**
     * @param isBig True to get the width of big paths, false for small ones.
     * @return The corresponding width of the path.
     */
    public static int getWidth(boolean isBig){
        //TODO Make MapPath width culture specific.
        return isBig ? BIG_WIDTH : SMALL_WIDTH;
    }
}
