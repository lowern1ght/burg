package com.dotteam.onceuponatown.entity.ai.goal.core;

import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.entity.ai.goal.NpcGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class NpcPanicGoal extends NpcGoal {
    protected double posX;
    protected double posY;
    protected double posZ;

    public NpcPanicGoal(Npc npc) {
        super(npc);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    public boolean canUse() {
        if (!this.shouldPanic())
            return false;
        if (this.npc.isOnFire()) {
            BlockPos blockpos = this.lookForWater();
            if (blockpos != null) {
                this.posX = blockpos.getX();
                this.posY = blockpos.getY();
                this.posZ = blockpos.getZ();
                return true;
            }
        }
        return this.findRandomPosition();
    }

    public boolean canContinueToUse() {
        return !this.npc.getNavigation().isDone();
    }

    public void start() {
        this.npc.getNavigation().moveTo(this.posX, this.posY, this.posZ, this.speedModifier);
    }

    protected boolean shouldPanic() {
        return this.npc.isFreezing() || this.npc.isOnFire();
    }

    protected boolean findRandomPosition() {
        Vec3 vec3 = DefaultRandomPos.getPos(this.npc, 5, 4);
        if (vec3 == null)
            return false;
        this.posX = vec3.x;
        this.posY = vec3.y;
        this.posZ = vec3.z;
        return true;
    }

    @Nullable
    protected BlockPos lookForWater() {
        BlockGetter level = this.npc.level();
        BlockPos blockpos = this.npc.blockPosition();
        return !level.getBlockState(blockpos).getCollisionShape(level, blockpos).isEmpty() ? null :
                BlockPos.findClosestMatch(blockpos, 20, 10, (posCandidate) -> level.getFluidState(posCandidate).is(FluidTags.WATER)).orElse(null);
    }
}
