package org.dawnoftime.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.Utils;

import java.util.Locale;

public record BlockInfo(BlockPos pos, BlockState state, CompoundTag nbt) {
    public String toString() {
        return String.format(Locale.ROOT, "<BlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
    }

    public BlockInfo move(int x, int y, int z){
        return new BlockInfo(this.pos.offset(x, y, z), this.state, this.nbt);
    }

    /**
     * Rotate a BlockInfo in North direction to the new given direction : its position and the blockstate.
     * @param dir New direction.
     * @param xSize Total size x of the build.
     * @param zSize Total size z of the build.
     * @return A new instance of BlockInfo the direction is not North, with the pos and state correctly rotated.
     */
    public BlockInfo rotate(Direction dir, int xSize, int zSize){
        Rotation rotation = switch (dir){
            case WEST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        BlockState rotatedState = state.rotate(rotation);
        return dir == Direction.NORTH ? this : new BlockInfo(Utils.rotateInBuild(this.pos, dir, xSize, zSize), rotatedState, this.nbt);
    }
}
