package org.dawnoftime.onceuponatown.town.generation;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.building.NpcBuild;

import java.util.Collections;

import static net.minecraft.core.Direction.*;

public class TownMapUtils {

    public static final Direction[] NW_DIR_CYCLE = new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, NORTH};

    /**
     * Function that provides an iterator of MutablePos on a rectangular shape in Clockwise era.
     * @param originPos NORTH_WEST BlockPos of the rectangle.
     * @param sizeX Horizontal size of the rectangle.
     * @param sizeZ Vertical size of the rectangle.
     * @return An iterator that provides a Mutable BlockPos following the rectangle shape. Be careful to not move the provided
     * Mutable BlockPos, because it's position is not checked in this function.
     */
    public static Iterable<BlockPos.MutableBlockPos> rectangularPosIterator(BlockPos originPos, int sizeX, int sizeZ){
        // If, for some reason, the building has a size of 1×1, we just return the originPos.
        if(sizeX < 2 && sizeZ < 2){
            return Collections.singletonList(originPos.mutable());
        }
        return () -> new AbstractIterator<>() {
            private final BlockPos.MutableBlockPos cursor = originPos.mutable();
            private int cursorDir;
            private final int[] moves = new int[]{sizeX - 1, sizeZ - 1, sizeX - 1, sizeZ - 1};

            @Override
            protected BlockPos.MutableBlockPos computeNext() {
                while(this.moves[this.cursorDir] <= 0){
                    this.cursorDir++;
                    if(this.cursorDir > 3){
                        return this.endOfData();
                    }
                }
                this.cursor.move(NW_DIR_CYCLE[this.cursorDir]);
                this.moves[this.cursorDir]--;
                return this.cursor;
            }
        };
    }

    public enum Corner{
        NORTH_WEST(WEST, NORTH),
        NORTH_EAST(NORTH, EAST),
        SOUTH_EAST(EAST, SOUTH),
        SOUTH_WEST(SOUTH, WEST);
        private final Direction leftDir;
        private final Direction rightDir;

        Corner(Direction leftDir, Direction rightDir){
            this.leftDir = leftDir;
            this.rightDir = rightDir;
        }

        public Direction getLeftDirection(){
            return this.leftDir;
        }

        public Direction getRightDirection(){
            return this.rightDir;
        }

        private int getStepX(){
            return this.leftDir.getStepX() + this.rightDir.getStepX();
        }

        private int getStepZ(){
            return this.leftDir.getStepZ() + this.rightDir.getStepZ();
        }

        /**
         * Function that allows to get the origin (NORTH_WEST corner) of a Build placed using this corner.
         * @param pos Position of the corner.
         * @param build Build to be placed.
         * @param buildDir Direction to which the Build will be oriented.
         * @return The position of the NORTH_WEST corner of the Build if it is placed on this corner.
         */
        public BlockPos getOrigin(BlockPos pos, NpcBuild build, Direction buildDir){
            return this.getCornerPos(pos, build, buildDir, NORTH_WEST);
        }

        /**
         * Function that returns the BlockPos of the given targetCorner, based on this corner's position.
         * @param pos BlockPos of this corner.
         * @param build This corner's MapBuild.
         * @param buildDir The direction of the MapBuild.
         * @param targetCorner The corner we want to obtain.
         * @return The BlockPos of the targetCorner.
         */
        public BlockPos getCornerPos(BlockPos pos, NpcBuild build, Direction buildDir, Corner targetCorner){
            int signOffsetX = (targetCorner.getStepX() - this.getStepX()) / 2;
            int signOffsetZ = (targetCorner.getStepZ() - this.getStepZ()) / 2;
            return pos.offset(signOffsetX * (build.getSizeX(buildDir) - 1), 0, signOffsetZ * (build.getSizeZ(buildDir) - 1));
        }

        /**
         * @param dirVector Direction of the vector.
         * @param cornerOnTheRight True to return the Corner on the right side of the direction vector, false
         *                         to get the Corner on the left side.
         * @return One of the 2 corners adjacent to a vector toward the given direction.
         */
        public static Corner getCornerNextToDir(Direction dirVector, boolean cornerOnTheRight) {
            return switch (dirVector){
                case NORTH -> cornerOnTheRight ? SOUTH_WEST : SOUTH_EAST;
                case EAST -> cornerOnTheRight ? NORTH_WEST : SOUTH_WEST;
                case SOUTH -> cornerOnTheRight ? NORTH_EAST : NORTH_WEST;
                case WEST -> cornerOnTheRight ? SOUTH_EAST : NORTH_EAST;
                default -> throw new IllegalStateException("Unexpected direction: " + dirVector + ". Map Corner can only exist on the horizontal plane.");
            };
        }
    }
}
