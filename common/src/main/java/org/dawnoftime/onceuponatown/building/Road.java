package org.dawnoftime.onceuponatown.building;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.dawnoftime.onceuponatown.building.type.SliceBuildType;
import org.dawnoftime.onceuponatown.culture.Culture;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.town.generation.ProtoTown;
import org.dawnoftime.onceuponatown.town.generation.TownMapUtils;
import org.dawnoftime.onceuponatown.town.generation.bud.BuildBud;

import java.util.ArrayList;
import java.util.Objects;

import static org.dawnoftime.onceuponatown.Config.*;
import static org.dawnoftime.onceuponatown.town.generation.ProtoTown.RANDOM;

/**
 * Town's Roads are special Builds that automatically grow when new Buildings are placed in the Town
 */
public class Road extends SliceBuild {
    private final boolean isWide;
    private boolean canGrow = true;

    public Road(SliceBuildType sliceBuildType, int length, int level) {
        super(sliceBuildType, length, level);
        isWide = Objects.equals(sliceBuildType.getId(), DataHandler.WIDE_ROAD_TYPE_NAME);
    }

    protected Road(Culture culture, CompoundTag tag) {
        super(culture, tag);
        isWide = tag.getBoolean("IsWide");
        canGrow = tag.getBoolean("CanGrow");
    }

    @Override
    public CompoundTag saveNbt() {
        CompoundTag tag = super.saveNbt();
        tag.putBoolean("IsWide", isWide);
        tag.putBoolean("CanGrow", canGrow);
        return tag;
    }

    @Override
    public void onAddedToTown(ProtoTown town) {
        tryGrowing(town);
    }

    /**
     * Tries to grow this Road, and updates surrounding BuildBuds.
     *
     * @param town Town to which this Road belongs.
     */
    public void tryGrowing(ProtoTown town) {
        this.tryExtendLength(town);
        this.updateBuildingBuds(town);
        this.computeShape(town);
    }

    /**
     * Function that tries to extend the road. The road can only grow in its opposite direction.
     * This function will successively try : to stop the road growth, to turn, then to grow.
     * @param town Town where the road is located.
     */
    private void tryExtendLength(ProtoTown town) {
        if (this.canGrow) {
            // Deciding if this Road will definitively stop growing after this.
            if (!isWide && town.getBuds().size() > CRITICAL_BUDS_NUMBER) {
                if (RANDOM.nextFloat() < ROAD_STOP_RATE) {
                    canGrow = false;
                }
            }
            // If the road will stop growing, it should stop directly at the end of the adjacent Build.
            int bonusSize = canGrow ? MINI_ROAD_LENGTH : 0;
            int dirGrowth = this.getGrowthSize(town, bonusSize);
            if(dirGrowth > 0){
                // Updating the originPos of the Road depending on the growth.
                if (this.getDirection() == Direction.SOUTH || this.getDirection() == Direction.EAST) {
                    this.setOriginPos(this.getOriginPos().relative(this.getDirection().getOpposite(), dirGrowth));
                }
                // Updating the Road size which will also update the shape.
                this.extendLength(dirGrowth);
                // Lastly, special Buds will be added to the Town : bridge or stairs
                town.updateTownMap(this);
            }
        }
    }

    /**
     * Extends the length of this road. Warning, the yShape needs to be updated afterward.
     * @param extendInDir         Number of blocks extended in this build's direction.
     */
    private void extendLength(int extendInDir) {
        length += extendInDir;
        // We move the YShape so that the locked shapes stay at the same position.
        SliceProperty[] newYShape = new SliceProperty[length];
        System.arraycopy(yShape, 0, newYShape, 0, yShape.length);
        yShape = newYShape;
    }

    /**
     * @param town Town this Road belongs to.
     * @param bonusSize Extra size that the Road should have after the last adjacent Build. This value is 0 if the Road
     *                  stops growing, thus it will stop definitively at the end of its adjacent Build.
     * @return The total number of block this Road should grow in the given direction to reach the border of adjacent buildings
     * plus the DEFAULT_PATH_LENGTH. Returns 0 if this Road already stops at the correct position.
     */
    private int getGrowthSize(ProtoTown town, int bonusSize) {
        int growth = -bonusSize;
        int emptyAdjacentBlocks = 0;
        Direction dir = this.getDirection().getOpposite();
        BlockPos initPos = this.getCornerPos(TownMapUtils.Corner.getCornerNextToDir(this.getDirection(), false)).relative(dir, 1 - bonusSize);
        BlockPos.MutableBlockPos adjLeftCursor = initPos.relative(dir.getClockWise(), -1).mutable();
        BlockPos.MutableBlockPos pathLeftCursor = initPos.mutable();
        BlockPos.MutableBlockPos pathRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir) - 1).mutable();
        BlockPos.MutableBlockPos adjRightCursor = initPos.relative(dir.getClockWise(), this.getSize(dir)).mutable();
        while (true) {
            // While the cursor is at an empty vec3 (or the vec3 contains this block), we can extend this Road.
            //TODO Check if there is a Y difference too big : we stop and will make a tower Bud.
            boolean a = town.isFreeAt(pathLeftCursor, this);
            boolean b = town.isFreeAt(pathRightCursor, this);
            if (town.isFreeAt(pathLeftCursor, this) && town.isFreeAt(pathRightCursor, this)) {
                growth++;
                emptyAdjacentBlocks++;
                if (!town.isFreeAt(adjLeftCursor) || !town.isFreeAt(adjRightCursor)) {
                    // If one of the 2 current adjacent blocks are not empty, we reset the number of empty adjacent blocks.
                    emptyAdjacentBlocks = 0;
                }
                if (emptyAdjacentBlocks >= bonusSize) {
                    // If the number of empty adjacent blocks has reached the minimal bonusSize, then the road stops growing.
                    return growth;
                }
                adjLeftCursor.move(dir);
                pathLeftCursor.move(dir);
                pathRightCursor.move(dir);
                adjRightCursor.move(dir);
            } else {
                return growth;
            }
        }
    }

    /**
     * Creates the Buds on the sides of this Road. Some Buds can be added at the top and bottom only if it can not grow.
     * @param town Town in which Buds are added
     */
    private void updateBuildingBuds(ProtoTown town) {
        ArrayList<BuildBud> newBuildBuds = new ArrayList<>();
        if (this.getDirection().getAxis() == Direction.Axis.X) {
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_WEST, this.getSizeX()));
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_EAST, this.getSizeX()));
            if (!canGrow) {
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_EAST, this.getSizeZ()));
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_WEST, this.getSizeZ()));
            }
        } else {
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_EAST, this.getSizeZ()));
            newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_WEST, this.getSizeZ()));
            if (!canGrow) {
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.NORTH_WEST, this.getSizeX()));
                newBuildBuds.addAll(this.findBudsOnSide(town, TownMapUtils.Corner.SOUTH_EAST, this.getSizeX()));
            }
        }
        newBuildBuds.forEach(town::tryCreateRoad);
    }

    /**
     * Creates Buds based on adjacent content in the Town map, on a line starting clockwise from the cornerPos.
     *
     * @param town       Town in which Buds are added.
     * @param corner     Corner to start the exploration. The side studied is always the rightDirection from this corner (i.e.
     *                   for NORTH_WEST, we will study the NORTH side of this Road).
     * @param sideLength Size of the size to study.
     * @return A list that contains the Buds created in this function.
     */
    private ArrayList<BuildBud> findBudsOnSide(ProtoTown town, TownMapUtils.Corner corner, int sideLength) {
        // We move the start BlockPos one block out of the Build in diagonal.
        BlockPos cornerPos = this.getCornerPos(corner).relative(corner.getRightDirection()).relative(corner.getLeftDirection());
        BlockPos.MutableBlockPos cursor = cornerPos.mutable();
        boolean isPreviousPosEmpty = town.isFreeAt(cursor);
        cursor.move(corner.getLeftDirection(), -1);
        boolean isCurrentPosEmpty;
        ArrayList<BuildBud> buildBuds = new ArrayList<>();
        // We create an array that contains whenever each BlockPos is empty or not.
        for (int i = 1; i < sideLength + 2; i++) {
            isCurrentPosEmpty = town.isFreeAt(cursor);
            // We modify the value for the start and the end of the loop.
            if (i == 1) {
                isPreviousPosEmpty &= isCurrentPosEmpty;
            }
            if (i == sideLength + 1) {
                isCurrentPosEmpty &= isPreviousPosEmpty;
            }
            // Finally we create the Bud if needed.
            if (isCurrentPosEmpty != isPreviousPosEmpty) {
                buildBuds.add(town.addBud(this.setupBud(town, cursor.immutable(), isCurrentPosEmpty, corner.getRightDirection())));
            }
            isPreviousPosEmpty = isCurrentPosEmpty;
            cursor.move(corner.getLeftDirection(), -1);
        }
        return buildBuds;
    }

    /**
     * Creates a Bud at the currentPos or position before depending on which one is empty.
     *
     * @param town              Town is which we want to create a Bud.
     * @param currentPos        Current position of the cursor.
     * @param isCurrentPosEmpty True if the current position of the cursor is empty, false otherwise.
     * @param budDir            Direction oriented at the opposite of the Road, that corresponds to the RightDir of the Corner.
     * @return The created Bud instance.
     */
    private BuildBud setupBud(ProtoTown town, BlockPos currentPos, boolean isCurrentPosEmpty, Direction budDir) {
        BlockPos previousPos = currentPos.relative(budDir.getCounterClockWise());
        Direction[] pathDirection = new Direction[town.getBuild(isCurrentPosEmpty ? previousPos : currentPos) instanceof SliceBuild ? 2 : 1];
        pathDirection[0] = budDir.getOpposite();
        if (pathDirection.length > 1) {
            pathDirection[1] = isCurrentPosEmpty ? budDir.getCounterClockWise() : budDir.getClockWise();
        }
        return new BuildBud(BuildBud.BudType.DEFAULT, town, isCurrentPosEmpty ? currentPos.getX() : previousPos.getX(), isCurrentPosEmpty ? currentPos.getZ() : previousPos.getZ(), TownMapUtils.Corner.getCornerNextToDir(budDir, isCurrentPosEmpty), pathDirection);
    }

    @Override
    public CompoundTag getGuiDescription() {
        CompoundTag descriptionTag = super.getGuiDescription();
        descriptionTag.putByte("Category", Build.ROAD);
        descriptionTag.putBoolean("IsWide", isWide);
        descriptionTag.putBoolean("CanGrow", canGrow);
        return descriptionTag;
    }

    public boolean isWide() {
        return isWide;
    }

    @Override
    protected byte getBuildCategory() {
        return ROAD;
    }
}
