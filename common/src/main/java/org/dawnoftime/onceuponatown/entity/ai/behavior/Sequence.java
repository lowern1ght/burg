package org.dawnoftime.onceuponatown.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Sequence<E extends LivingEntity> implements BehaviorControl<E> {
    private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition;
    private final Set<MemoryModuleType<?>> exitErasedMemories;
    private final List<BehaviorControl<? super E>> behaviors; // Behaviors to run, one after another. Only one behavior can run at any given time.
    private final boolean acceptFailure; // If true, sequence will move no the next behavior in the list if one fails to start.
    private Behavior.Status status = Behavior.Status.STOPPED;
    private int step; // List index of the current behavior.
    private boolean stepStarted; // If the current behavior has started or not.

    public Sequence(
        Map<MemoryModuleType<?>, MemoryStatus> entryCondition,
        Set<MemoryModuleType<?>> exitErasedMemories,
        List<BehaviorControl<? super E>> behaviors,
        boolean acceptFailure
    ) {
        this.entryCondition = entryCondition;
        this.exitErasedMemories = exitErasedMemories;
        this.behaviors = behaviors;
        this.acceptFailure = acceptFailure;
    }

    private boolean hasRequiredMemories(E entity) {
        for (Map.Entry<MemoryModuleType<?>, MemoryStatus> entry : this.entryCondition.entrySet()) {
            MemoryModuleType<?> memoryModuleType = entry.getKey();
            MemoryStatus memoryStatus = entry.getValue();
            if (!entity.getBrain().checkMemory(memoryModuleType, memoryStatus)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean tryStart(ServerLevel level, E entity, long gameTime) {
        if (!behaviors.isEmpty() && hasRequiredMemories(entity)) {
            this.status = Behavior.Status.RUNNING;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void tickOrStop(ServerLevel level, E entity, long gameTime) {
        var behavior = behaviors.get(step);
        if (!stepStarted) {
            stepStarted = behavior.tryStart(level, entity, gameTime);
            if (!stepStarted && !acceptFailure) {
                ++step;
                if (step >= behaviors.size()) {
                    doStop(level, entity, gameTime);
                }
            }
        } else {
            if (behavior.getStatus() == Behavior.Status.RUNNING) {
                behavior.tickOrStop(level, entity, gameTime);
            } else {
                ++step;
                stepStarted = false;
                if (step >= behaviors.size()) {
                    doStop(level, entity, gameTime);
                }
            }
        }
    }

    @Override
    public void doStop(ServerLevel level, E entity, long gameTime) {
        status = Behavior.Status.STOPPED;
        behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .forEach(behavior -> behavior.doStop(level, entity, gameTime));
        exitErasedMemories.forEach(entity.getBrain()::eraseMemory);
        step = 0;
        stepStarted = false;
    }

    @Override
    public Behavior.Status getStatus() {
        return status;
    }

    @Override
    public String debugString() {
        return getClass().getSimpleName();
    }

    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }
}
