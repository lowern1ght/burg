package org.dawnoftime.onceuponatown.construction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.Utils;

import java.util.Locale;

public record EntityInfo(Vec3 vec3, BlockPos pos, CompoundTag entityNbt) {
    public String toString() {
        return String.format(Locale.ROOT, "<EntityInfo | %s | %s | %s>", this.vec3, this.pos, this.entityNbt);
    }

    public EntityInfo below(int y){
        return new EntityInfo(this.vec3.subtract(0, y, 0), this.pos.below(y), this.entityNbt);
    }

    public EntityInfo moveAndRotateAround(int x, int y, int z){
        return new EntityInfo(this.vec3.add(x, y, z), this.pos.offset(x, y, z), this.entityNbt);
    }

    /**
     * Rotate a EntityInfo in North direction to the new given direction : its position and the blockstate.
     * @param dir New direction.
     * @param xSize Total size x of the build.
     * @param zSize Total size z of the build.
     * @return A new instance of EntityInfo the direction is not North, with the pos and state correctly rotated.
     */
    public EntityInfo rotate(Direction dir, int xSize, int zSize){
        return dir == Direction.NORTH ? this : new EntityInfo(
                Utils.rotateInBuild(this.vec3, dir, xSize, zSize),
                Utils.rotateInBuild(this.pos, dir, xSize, zSize),
                this.entityNbt);
    }
}
