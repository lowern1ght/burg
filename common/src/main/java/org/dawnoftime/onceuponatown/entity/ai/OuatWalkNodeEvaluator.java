package org.dawnoftime.onceuponatown.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class OuatWalkNodeEvaluator extends WalkNodeEvaluator {

    // Fence gates are treated as wooden doors by the pathfinder so the NPC routes through
    // them instead of treating them as solid walls. The reactive OpenFenceGateGoal still
    // handles the physical open/close; this evaluator only teaches the route planner.
    @Override
    public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z) {
        BlockState state = level.getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof FenceGateBlock) {
            return BlockPathTypes.WALKABLE;
        }
        return super.getBlockPathType(level, x, y, z);
    }
}
