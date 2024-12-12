package org.dawnoftime.onceuponatown.town.generation.bud;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import org.dawnoftime.onceuponatown.building.Build;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils.Corner;

import java.util.Objects;

import static org.dawnoftime.onceuponatown.Config.BUD_MINIMAL_SPACE;

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


    /**
     * Create a new instance of Bud.
     * @param type          Type of this bud, depending on the building it will be able to support.
     * @param xPos       BlockPos of the bud. The Y value doesn't matter : it will be recalculated on placement.
     * @param zPos       BlockPos of the bud. The Y value doesn't matter : it will be recalculated on placement.
     * @param corner        Corner type of this bud.
     * @param adjacentPaths Direction where there is a MapPath from the realPos.
     */
    public BuildBud(BudType type, ProtoTown town, int xPos, int zPos, Corner corner, Direction[] adjacentPaths) {
        this.type = type;
        this.realPos = new BlockPos(xPos, town.getSurfaceY(xPos, zPos), zPos);
        this.corner = corner;
        this.adjacentPaths = adjacentPaths;
    }

    /**
     * Creates an instance of Bud from the NBT tag.
     * @param tag CompoundTag that contains all the information needed to create the bud.
     */
    public BuildBud(CompoundTag tag){
        this.type = BudType.valueOf(tag.getString("Type"));
        this.realPos = NbtUtils.readBlockPos(tag.getCompound("RealPos"));
        this.corner = Corner.valueOf(tag.getString("Corner"));
        ListTag tags = tag.getList("AdjacentPaths", ListTag.TAG_STRING);
        this.adjacentPaths = tags.stream()
                .map(tagElement -> Direction.byName(tagElement.getAsString()))
                .toArray(Direction[]::new);
    }

    public CompoundTag writeNBT(){
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.put("RealPos", NbtUtils.writeBlockPos(this.realPos));
        tag.putString("Corner", this.corner.toString());
        ListTag tags = new ListTag();
        for(Direction dir: this.adjacentPaths){
            tags.add(StringTag.valueOf(dir.toString()));
        }
        tag.put("AdjacentPaths", tags);
        return tag;
    }

    /**
     * @return The squared distance to the town center.
     */
    public int getSquaredDistToCenter() {
        return this.squaredDistToCenter;
    }

    /**
     * Function that updates the squared distance between this bud and the town center.
     * @param townCenterPos TownMap of the bud.
     */
    public void setSquaredDistToCenter(BlockPos townCenterPos) {
        int difX = this.realPos.getX() - townCenterPos.getX();
        int difZ = this.realPos.getZ() - townCenterPos.getZ();
        this.squaredDistToCenter = difX * difX + difZ * difZ;
    }

    /**
     * @return Returns a list that contains the direction of the adjacent MapPaths.
     */
    public Direction[] getAdjacentRoads() {
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
    public BlockPos findOriginPos(Build build, Direction dir) {
        BlockPos origin = this.corner.getOrigin(this.realPos, build, dir);
        return origin.atY(build.findAdaptedY(origin, dir));
    }

    /**
     * Check if this bud as enough free space around him to place a build in the future.
     * @param map The TownMap of this Bud.
     * @return True if the available maximal rectangle is bigger than the minimal square defined in the configs.
     */
    public boolean asEnoughSpace(ProtoTown map) {
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
    private boolean asEnoughSpace(ProtoTown map, boolean clockwise) {
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj){
            return true;
        }
        if(obj instanceof BuildBud bud){
            return (bud.realPos.getX() == this.realPos.getX()
                    && bud.realPos.getZ() == this.realPos.getZ()
                    && bud.corner == this.corner
                    && bud.type == this.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(realPos.getX(), realPos.getZ(), corner, type);
    }

    public enum BudType{

        DEFAULT(),
        AGRICULTURAL(),
        BRIDGE();

        BudType(){

        }
    }
}
