package org.dawnoftime.onceuponatown.town.generation;

import com.google.common.collect.AbstractIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.dawnoftime.onceuponatown.building.Build;

import java.util.Collections;

import static net.minecraft.core.Direction.*;

/**
 * Utilities for placing Builds in Towns.
 */
public class TownMapUtils {
    public static final Direction[] NW_DIR_CYCLE = new Direction[]{Direction.EAST, Direction.SOUTH, Direction.WEST, NORTH};

    /**
     * Provides an iterator of MutableBlockPos on a rectangular shape in clockwise order.
     * @param originPos NORTH_WEST BlockPos of the rectangle.
     * @param sizeX     horizontal size of the rectangle.
     * @param sizeZ     vertical size of the rectangle.
     * @return an iterator that provides a MutableBlockPos following the rectangle shape. Be careful to not move the provided
     * MutableBlockPos, because it's position is not checked in this method.
     */
    public static Iterable<BlockPos.MutableBlockPos> rectangularPosIterator(BlockPos originPos, int sizeX, int sizeZ) {
        // If, for some reason, the building has a size of 1×1, we just return the originPos.
        if (sizeX < 2 && sizeZ < 2) {
            return Collections.singletonList(originPos.mutable());
        }
        return () -> new AbstractIterator<>() {
            private final BlockPos.MutableBlockPos cursor = originPos.mutable();
            private int cursorDir;
            private final int[] moves = new int[]{sizeX - 1, sizeZ - 1, sizeX - 1, sizeZ - 1};

            @Override
            protected BlockPos.MutableBlockPos computeNext() {
                while (this.moves[this.cursorDir] <= 0) {
                    this.cursorDir++;
                    if (this.cursorDir > 3) {
                        return this.endOfData();
                    }
                }
                this.cursor.move(NW_DIR_CYCLE[this.cursorDir]);
                this.moves[this.cursorDir]--;
                return this.cursor;
            }
        };
    }

    public enum Corner {
        NORTH_WEST(WEST, NORTH),
        NORTH_EAST(NORTH, EAST),
        SOUTH_EAST(EAST, SOUTH),
        SOUTH_WEST(SOUTH, WEST);
        private final Direction leftDir;
        private final Direction rightDir;

        Corner(Direction leftDir, Direction rightDir) {
            this.leftDir = leftDir;
            this.rightDir = rightDir;
        }

        public Direction getLeftDirection() {
            return this.leftDir;
        }

        public Direction getRightDirection() {
            return this.rightDir;
        }

        private int getStepX() {
            return this.leftDir.getStepX() + this.rightDir.getStepX();
        }

        private int getStepZ() {
            return this.leftDir.getStepZ() + this.rightDir.getStepZ();
        }

        /**
         * Gets the NORTH_WEST position of a Build placed on this Corner.
         *
         * @param cornerPos      position of this Corner.
         * @param build    Build placed using this Corner.
         * @param buildDir Direction of the Build.
         * @return the position of the NORTH_WEST Corner of the Build.
         */
        public BlockPos getOriginPosOfBuild(BlockPos cornerPos, Build build, Direction buildDir) {
            return this.getCornerPosOfBuild(cornerPos, build, buildDir, NORTH_WEST);
        }

        /**
         * Gets the position of a target Corner of a Build placed using this Corner.
         *
         * @param cornerPos          BlockPos of this Corner.
         * @param build        Build placed using this Corner.
         * @param buildDir     Direction of the Build.
         * @param targetCorner the target Corner.
         * @return the BlockPos of the given target Corner, based on this Corner's position.
         */
        public BlockPos getCornerPosOfBuild(BlockPos cornerPos, Build build, Direction buildDir, Corner targetCorner) {
            int signOffsetX = (targetCorner.getStepX() - this.getStepX()) / 2;
            int signOffsetZ = (targetCorner.getStepZ() - this.getStepZ()) / 2;
            return cornerPos.offset(signOffsetX * (build.getSizeX(buildDir) - 1), 0, signOffsetZ * (build.getSizeZ(buildDir) - 1));
        }

        /**
         * @param dirVector        Direction of the vector.
         * @param cornerOnTheRight true to return the Corner on the right side of the direction vector, false
         *                         to get the Corner on the left side.
         * @return one of the 2 corners adjacent to a vector toward the given direction.
         */
        public static Corner getCornerNextToDir(Direction dirVector, boolean cornerOnTheRight) {
            return switch (dirVector) {
                case NORTH -> cornerOnTheRight ? SOUTH_WEST : SOUTH_EAST;
                case EAST -> cornerOnTheRight ? NORTH_WEST : SOUTH_WEST;
                case SOUTH -> cornerOnTheRight ? NORTH_EAST : NORTH_WEST;
                case WEST -> cornerOnTheRight ? SOUTH_EAST : NORTH_EAST;
                default ->
                    throw new IllegalStateException("Unexpected direction: " + dirVector + ". Map Corner can only exist on the horizontal plane.");
            };
        }
    }
}
