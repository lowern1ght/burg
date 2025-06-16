package org.dawnoftime.onceuponatown.entity.ai.task;

import com.google.common.collect.ImmutableMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import org.dawnoftime.onceuponatown.entity.Npc;

public class TradeWithPlayer extends Behavior<Npc> {
    private final float speedModifier;

    public TradeWithPlayer(float speedModifier) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Npc npc) {
        Player player = npc.getInteractingPlayer();
        return npc.isAlive() && player != null && !npc.isInWater() && !npc.hurtMarked && npc.distanceToSqr(player) <= 16.0 && player.containerMenu != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Npc npc, long gameTime) {
        return this.checkExtraStartConditions(level, npc);
    }

    @Override
    protected void start(ServerLevel level, Npc npc, long gameTime) {
        this.followPlayer(npc);
    }

    @Override
    protected void stop(ServerLevel level, Npc npc, long gameTime) {
        Brain<?> brain = npc.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel level, Npc npc, long gameTime) {
        this.followPlayer(npc);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        return false;
    }

    private void followPlayer(Npc npc) {
        Player player = npc.getInteractingPlayer();
        if (player != null) {
            Brain<?> brain = npc.getBrain();
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), this.speedModifier, 2));
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        }
    }
}
