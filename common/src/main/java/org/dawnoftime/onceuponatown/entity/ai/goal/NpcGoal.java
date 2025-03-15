package org.dawnoftime.onceuponatown.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.SimpleStateMachine;

import java.util.function.Consumer;

public abstract class NpcGoal extends Goal {
    protected final Npc npc;
    protected double speedModifier = 1.0D;
    private IntProvider cooldownProvider = ConstantInt.of(-1);
    private IntProvider durationProvider = ConstantInt.of(Integer.MAX_VALUE);
    private int runningTime = 0;
    private int expirationTime = Integer.MAX_VALUE;
    private long nextAvailableTime = 0;
    private boolean timeLimitedGoal = false;
    private Consumer<NpcGoal> onStart = goal -> {
    };
    private Consumer<NpcGoal> onStop = goal -> {
    };
    protected SimpleStateMachine stateMachine;

    protected NpcGoal(Npc npc) {
        this.npc = npc;
    }

    public NpcGoal speedModifier(double speedModifier) {
        this.speedModifier = speedModifier;
        return this;
    }

    public NpcGoal duration(IntProvider durationProvider) {
        this.durationProvider = durationProvider;
        this.timeLimitedGoal = true;
        return this;
    }

    public NpcGoal cooldown(IntProvider cooldownProvider) {
        this.cooldownProvider = cooldownProvider;
        return this;
    }

    @Override
    public boolean canUse() {
        return this.nextAvailableTime < this.npc.level().getGameTime();
    }

    @Override
    public void start() {
        this.runningTime = 0;
        this.expirationTime = this.durationProvider.sample(this.npc.getRandom());
        this.onStart.accept(this);
    }

    public NpcGoal onStart(Consumer<NpcGoal> consumer) {
        this.onStart = consumer;
        return this;
    }

    @Override
    public void stop() {
        this.nextAvailableTime = this.npc.level().getGameTime() + this.cooldownProvider.sample(this.npc.getRandom());
        this.onStop.accept(this);
    }

    public NpcGoal onStop(Consumer<NpcGoal> consumer) {
        this.onStop = consumer;
        return this;
    }

    @Override
    public void tick() {
        ++this.runningTime;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return this.timeLimitedGoal;
    }

    public int getRunningTime() {
        return this.runningTime;
    }

    protected Level level() {
        return npc.level();
    }

    protected BlockPos npcPos() {
        return npc.blockPosition();
    }
}
