package org.dawnoftime.onceuponatown.entity.ai.task;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.dawnoftime.onceuponatown.entity.ai.task.base.Task;

import java.util.function.Function;

public class GoToPosition<E extends LivingEntity> extends Task<E> {
    private final Function<E, BlockPos> destinationProvider;
    private final int closeEnoughDistance;
    private final float speedModifier;
    private int retryTick;

    public GoToPosition(Function<E, BlockPos> destinationProvider, int closeEnoughDistance, float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
        this.destinationProvider = destinationProvider;
        this.closeEnoughDistance = closeEnoughDistance;
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return destinationProvider.apply(owner).distManhattan(owner.blockPosition()) > closeEnoughDistance;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        BehaviorUtils.setWalkAndLookTargetMemories(entity, destinationProvider.apply(entity).above(), speedModifier, closeEnoughDistance);
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        retryTick++;
        if (retryTick > 80) {
            retryTick = 0;
            BehaviorUtils.setWalkAndLookTargetMemories(entity, destinationProvider.apply(entity).above(), speedModifier, closeEnoughDistance);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime) && checkExtraStartConditions(level, entity);
    }
}


