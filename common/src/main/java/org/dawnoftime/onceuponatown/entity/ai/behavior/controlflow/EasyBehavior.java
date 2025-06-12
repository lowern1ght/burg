package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * Improved base class for behaviors
 */
public class EasyBehavior<E extends LivingEntity> extends Behavior<E> {
    protected final Set<MemoryModuleType<?>> memoriesToEraseOnStop = new HashSet<>();
    protected Predicate<E> startPredicate = entity -> true;
    protected Predicate<E> stopPredicate = entity -> false;
    protected Consumer<E> onStart = entity -> {
    };
    protected Consumer<E> onTick = entity -> {
    };
    protected Consumer<E> onStop = entity -> {
    };
    protected ToIntFunction<E> durationProvider = entity -> Integer.MAX_VALUE;
    protected ToIntFunction<E> cooldownProvider = entity -> 0;
    protected long cooldownEnd = 0L;

    public EasyBehavior(@NotNull Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        super(entryCondition);
    }

    public EasyBehavior() {
        this(new HashMap<>());
    }

    protected boolean canStart(ServerLevel level, E entity, long gameTime) {
        return hasRequiredMemories(entity)
            && cooldownEnd <= gameTime
            && startPredicate.test(entity)
            && checkExtraStartConditions(level, entity); // True by default
    }

    @Override
    public final boolean tryStart(ServerLevel level, E entity, long gameTime) {
        if (canStart(level, entity, gameTime)) {
            status = Status.RUNNING;
            endTimestamp = gameTime + durationProvider.applyAsInt(entity);
            start(level, entity, gameTime);
            return true;
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        onStart.accept(entity);
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        onTick.accept(entity);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return !stopPredicate.test(entity); // Behaviors are not one shot by default
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        cooldownEnd = gameTime + cooldownProvider.applyAsInt(entity);
        var brain = entity.getBrain();
        memoriesToEraseOnStop.forEach(brain::eraseMemory);
        onStop.accept(entity);
    }

    public EasyBehavior<E> startIf(Predicate<E> predicate) {
        startPredicate = predicate;
        return this;
    }

    public EasyBehavior<E> onStart(Consumer<E> consumer) {
        onStart = consumer;
        return this;
    }

    public EasyBehavior<E> onTick(Consumer<E> consumer) {
        onTick = consumer;
        return this;
    }

    public EasyBehavior<E> stopIf(Predicate<E> predicate) {
        stopPredicate = predicate;
        return this;
    }

    public EasyBehavior<E> onStop(Consumer<E> consumer) {
        onStop = consumer;
        return this;
    }

    public EasyBehavior<E> maxDuration(ToIntFunction<E> provider) {
        durationProvider = provider;
        return this;
    }

    public EasyBehavior<E> cooldown(ToIntFunction<E> provider) {
        cooldownProvider = provider;
        return this;
    }

    public EasyBehavior<E> requiresMemories(Map<MemoryModuleType<?>, MemoryStatus> memories) {
        entryCondition.putAll(memories);
        return this;
    }

    public EasyBehavior<E> requiresMemoriesAndEraseOnStop(Map<MemoryModuleType<?>, MemoryStatus> memories) {
        entryCondition.putAll(memories);
        eraseMemoriesOnStop(memories.keySet());
        return this;
    }

    public EasyBehavior<E> eraseMemoriesOnStop(Set<MemoryModuleType<?>> memories) {
        memoriesToEraseOnStop.addAll(memories);
        return this;
    }

    @Override
    public @NotNull String debugString() {
        return toString();
    }
}
