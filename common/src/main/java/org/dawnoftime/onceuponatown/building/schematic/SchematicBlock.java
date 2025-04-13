package org.dawnoftime.onceuponatown.building.schematic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.dawnoftime.onceuponatown.Utils;

import java.util.Locale;

public record SchematicBlock(BlockPos pos, BlockState state, CompoundTag nbt) {
    public String toString() {
        return String.format(Locale.ROOT, "<BlockInfo | %s | %s | %s>", this.pos, this.state, this.nbt);
    }

    public SchematicBlock move(int x, int y, int z) {
        return new SchematicBlock(this.pos.offset(x, y, z), this.state, this.nbt);
    }

    /**
     * Rotate a BlockInfo in North direction to the new given direction : its position and the blockstate.
     *
     * @param dir   New direction.
     * @param xSize Total size x of the build.
     * @param zSize Total size z of the build.
     * @return A new instance of BlockInfo the direction is not North, with the pos and state correctly rotated.
     */
    public SchematicBlock rotateInBuild(Direction dir, int xSize, int zSize) {
        Rotation rotation = switch (dir) {
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return dir == Direction.NORTH ? this : new SchematicBlock(Utils.rotateInBuild(this.pos, dir, xSize, zSize), state.rotate(rotation), this.nbt);
    }

    public SchematicBlock inverse() {
        return new SchematicBlock(this.pos, state.rotate(Rotation.CLOCKWISE_180), this.nbt);
    }
}
