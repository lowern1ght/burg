package org.dawnoftime.onceuponatown.town.generation.bud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.building.placement.BuildPlacement;
import org.dawnoftime.onceuponatown.town.generation.TownMap;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils.Corner;

import javax.annotation.Nullable;

import static org.dawnoftime.onceuponatown.town.generation.TownMapUtils.BUD_MINIMAL_SPACE;

/**
 * Each bud is a point at the intersection of two paths, or a path and a plot border.
 * Buds serve as corners for placing a new parcel, and this class contains the function to place correctly the buildings.
 */
public class BuildBud {

    private final BlockPos realPos;
    private int squaredDistToCenter;
    private final Corner corner;
    private final Direction[] adjacentPaths;
    private final BudType type;

    private BuildBud(TownMap map, BudType type, BlockPos realPos, Corner corner, Direction[] adjacentPaths) {
        this.type = type;
        this.realPos = realPos;
        this.corner = corner;
        this.setSquaredDistToCenter(map);
        this.adjacentPaths = adjacentPaths;
        map.addToBuds(this);
    }

    /**
     * Create a new instance of a Bud and adds it to the TownMap. If a similar Bud exists, returns null.
     *
     * @param map           TownMap of the bud.
     * @param type          Type of this bud, depending on the building it will be able to support.
     * @param realPos       BlockPos of the bud.
     * @param corner        Corner type of this bud.
     * @param adjacentPaths Direction where there is a MapPath from the realPos.
     * @return The new instance of Bud or null.
     */
    @Nullable
    public static BuildBud createBud(TownMap map, BudType type, BlockPos realPos, Corner corner, Direction[] adjacentPaths) {
        for (BuildBud buildBud : map.getBuds()) {
            if (buildBud.realPos.getX() == realPos.getX() && buildBud.realPos.getZ() == realPos.getZ()) {
                return null;
            }
        }
        //TODO Replace the Y with the Y value of the adjacent Path. Is it useful or do I just recalculate the correct Y when a build is set at the given position ?
        return new BuildBud(map, type, realPos, corner, adjacentPaths);
    }

    /**
     * Create a new instance of a Bud of DEFAULT type, and adds it to the TownMap. If a similar Bud exists, returns null.
     *
     * @param map           TownMap of the bud.
     * @param realPos       BlockPos of the bud.
     * @param corner        Corner type of this bud.
     * @param adjacentPaths Direction where there is a MapPath from the realPos.
     * @return The new instance of Bud or null.
     */
    @Nullable
    public static BuildBud createBud(TownMap map, BlockPos realPos, Corner corner, Direction[] adjacentPaths) {
        return createBud(map, BudType.DEFAULT, realPos, corner, adjacentPaths);
    }

    /**
     * @return The squared distance to the town center.
     */
    public int getSquaredDistToCenter() {
        return this.squaredDistToCenter;
    }

    /**
     * Function that updates the squared distance between this bud and the town center.
     *
     * @param map TownMap of the bud.
     */
    public void setSquaredDistToCenter(TownMap map) {
        this.squaredDistToCenter = this.getSquaredDistTo(map.getCenter());
    }

    /**
     * @param pos BlockPos to which we want to compute the horizontal distance.
     * @return The squared distance between the given pos and this Bud.
     */
    public int getSquaredDistTo(BlockPos pos) {
        int difX = this.realPos.getX() - pos.getX();
        int difZ = this.realPos.getZ() - pos.getZ();
        return difX * difX + difZ * difZ;
    }

    /**
     * @return Returns a list that contains the direction of the adjacent MapPaths.
     */
    public Direction[] getAdjacentPaths() {
        return this.adjacentPaths;
    }

    /**
     * @return This Bud's Corner type.
     */
    public Corner getCorner() {
        return this.corner;
    }

    /**
     * @return The real BlockPos of this Bud.
     */
    public BlockPos getRealPos() {
        return this.realPos;
    }

    /**
     * Function to get the NORTH_WEST real position of a given MapBuild placed on this Bud with the given rotation.
     *
     * @param build MapBuild to place.
     * @param dir   Direction of the MapBuild, used to get the size on X and Z axis.
     * @return The BlockPos of the origin of the MapBuild, at the correct Y.
     */
    public BlockPos findOriginPos(BuildPlacement build, Direction dir) {
        BlockPos origin = this.corner.getOrigin(this.realPos, build, dir);
        return origin.atY(build.findAdaptedY(origin, dir));
    }

    /**
     * Check if this bud as enough free space around him to place a build in the future.
     * @param map The TownMap of this Bud.
     * @return True if the available maximal rectangle is bigger than the minimal square defined in the configs.
     */
    public boolean asEnoughSpace(TownMap map) {
        if(this.asEnoughSpace(map, true)){
            return true;
        }
        return this.asEnoughSpace(map, false);
    }

    /**
     * Computes the bigger rectangle following empty blocks in clockwise or counter-clockwise rotation.
     * @param map The TownMap of this Bud.
     * @param clockwise True to rotate in clockwise rotation, false for counter-clockwise.
     * @return True if the rectangle is bigger than the minimal square defined in the configs.
     */
    private boolean asEnoughSpace(TownMap map, boolean clockwise) {
        int[] sizes = new int[4];
        Direction dir = clockwise ? this.getCorner().getLeftDirection() : this.getCorner().getRightDirection();
        dir = dir.getOpposite();
        BlockPos.MutableBlockPos cursor = this.getRealPos().mutable();
        int sideIndex = 0;
        // The last value of sizes will be different to 0 only if the loop was able to create a rectangle.
        while (sizes[3] == 0) {
            int max = sideIndex < 2 ? BUD_MINIMAL_SPACE : sizes[sideIndex - 2];
            sizes[sideIndex] = map.getEmptyLength(cursor, dir, max);
            // If the opposite side has a different size, we come back 2 side before, and restart the process with a shorter size.
            if(sideIndex >= 2){
                if(sizes[sideIndex] != sizes[sideIndex - 2]){
                    sizes[sideIndex - 2] = sizes[sideIndex];
                    sizes[sideIndex - 1] = 0;
                    sizes[sideIndex] = 0;
                    sideIndex -= 2;
                    dir = dir.getOpposite();
                }
            }

            // For next loop.
            sideIndex++;
            dir = clockwise ? dir.getClockWise() : dir.getCounterClockWise();
        }
        return sizes[0] >= BUD_MINIMAL_SPACE && sizes[1] >= BUD_MINIMAL_SPACE;
    }

    /**
     * @return The type of this Bud.
     */
    public BudType getType() {
        return this.type;
    }

    public enum BudType{

        DEFAULT(),
        AGRICULTURAL(),
        BRIDGE();

        BudType(){

        }
    }
}
