package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

public class OpenFenceGateGoal extends Goal {

    private final Mob mob;
    private BlockPos gatePos;
    private float openDirX;
    private float openDirZ;
    private boolean passed;

    public OpenFenceGateGoal(Mob mob) {
        this.mob = mob;
        setFlags(EnumSet.noneOf(Flag.class));
    }

    @Override
    public boolean canUse() {
        if (!mob.horizontalCollision) return false;
        gatePos = findClosedGate();
        return gatePos != null;
    }

    @Override
    public void start() {
        passed = false;
        openDirX = (float) (gatePos.getX() + 0.5 - mob.getX());
        openDirZ = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        BlockState state = mob.level().getBlockState(gatePos);
        if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
            mob.level().setBlock(gatePos, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return !passed;
    }

    @Override
    public void tick() {
        float dx = (float) (gatePos.getX() + 0.5 - mob.getX());
        float dz = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        if (openDirX * dx + openDirZ * dz < 0) passed = true;
    }

    @Override
    public void stop() {
        BlockState state = mob.level().getBlockState(gatePos);
        if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
            mob.level().setBlock(gatePos, state.setValue(FenceGateBlock.OPEN, false), Block.UPDATE_ALL);
        }
    }

    private BlockPos findClosedGate() {
        BlockPos npcPos = mob.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos check = npcPos.relative(dir);
            if (isClosedGate(check)) return check;
            if (isClosedGate(check.above())) return check.above();
        }
        return null;
    }

    private boolean isClosedGate(BlockPos pos) {
        BlockState state = mob.level().getBlockState(pos);
        return state.getBlock() instanceof FenceGateBlock
            && Boolean.FALSE.equals(state.getValue(FenceGateBlock.OPEN));
    }
}
