package org.dawnoftime.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public class ConstructionUtils {

    /**
     * Sort the list of blocks in the structure
     *
     * @param blockList The list of blocks in the structure
     */
    public static void sortBlocks(List<BlockInfo> blockList) {
        Comparator<BlockInfo> comparator = Comparator
                .<BlockInfo>comparingInt((pY) -> pY.pos().getY())
                .thenComparingInt((pX) -> pX.pos().getX())
                .thenComparingInt((pZ) -> pZ.pos().getZ());
        blockList.sort(comparator);
    }

    public static Vec3 transformEntityPos(Vec3 posInStructure, Mirror mirror, Rotation rotation, BlockPos rotationPivot) {
        double d0 = posInStructure.x;
        double d1 = posInStructure.y;
        double d2 = posInStructure.z;
        boolean flag = true;
        switch (mirror) {
            case LEFT_RIGHT -> d2 = 1.0D - d2;
            case FRONT_BACK -> d0 = 1.0D - d0;
            default -> flag = false;
        }

        int i = rotationPivot.getX();
        int j = rotationPivot.getZ();
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new Vec3((double) (i - j) + d2, d1, (double) (i + j + 1) - d0);
            case CLOCKWISE_90 -> new Vec3((double) (i + j + 1) - d2, d1, (double) (j - i) + d0);
            case CLOCKWISE_180 -> new Vec3((double) (i + i + 1) - d0, d1, (double) (j + j + 1) - d2);
            default -> flag ? new Vec3(d0, d1, d2) : posInStructure;
        };
    }

    public static BlockPos transformBlockPos(BlockPos posInStructure, Mirror mirror, Rotation rotation, BlockPos rotationPivot) {
        int i = posInStructure.getX();
        int j = posInStructure.getY();
        int k = posInStructure.getZ();
        boolean flag = true;
        switch (mirror) {
            case LEFT_RIGHT -> k = -k;
            case FRONT_BACK -> i = -i;
            default -> flag = false;
        }

        int l = rotationPivot.getX();
        int i1 = rotationPivot.getZ();
        return switch (rotation) {
            case COUNTERCLOCKWISE_90 -> new BlockPos(l - i1 + k, j, l + i1 - i);
            case CLOCKWISE_90 -> new BlockPos(l + i1 - k, j, i1 - l + i);
            case CLOCKWISE_180 -> new BlockPos(l + l - i, j, i1 + i1 - k);
            default -> flag ? new BlockPos(i, j, k) : posInStructure;
        };
    }

    public static BlockState transformBlockState(BlockState state, Mirror mirror, Rotation rotation) {
        return state.mirror(mirror).rotate(rotation);
    }
}