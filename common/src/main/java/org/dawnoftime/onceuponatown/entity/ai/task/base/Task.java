package org.dawnoftime.onceuponatown.entity.ai.task.base;

import com.mojang.datafixers.util.Pair;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.*;

/**
 * Improved base class for behaviors
 */
public class Task<E extends LivingEntity> extends Behavior<E> {
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
    private String debugInfo = "";
    private ServerLevel level;

    public Task(@NotNull Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        super(entryCondition);
    }

    public Task() {
        this(new HashMap<>());
    }

    private boolean canStart(ServerLevel level, E entity, long gameTime) {
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
            this.level = level;
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

    public Task<E> startIf(Predicate<E> predicate) {
        startPredicate = startPredicate.and(predicate);
        return this;
    }

    public Task<E> restrictTo(Function<E, BlockPos> position, int closeEnoughDistance) {
        startIf(npc -> npc.distanceToSqr(position.apply(npc).getCenter()) <= closeEnoughDistance * closeEnoughDistance);
        stopIf(npc -> npc.distanceToSqr(position.apply(npc).getCenter()) > closeEnoughDistance * closeEnoughDistance);
        return this;
    }

    public Task<E> probability(int percentChance) {
        startPredicate = startPredicate.and((e -> e.getRandom().nextInt(100) < percentChance));
        return this;
    }

    public Task<E> onStart(Consumer<E> consumer) {
        onStart = onStart.andThen(consumer);
        return this;
    }

    public Task<E> onTick(Consumer<E> consumer) {
        onTick = onTick.andThen(consumer);
        return this;
    }

    public Task<E> stopIf(Predicate<E> predicate) {
        stopPredicate = stopPredicate.or(predicate);
        return this;
    }

    /**
     * Stops this task if the brain is not running the required activity
     **/
    public Task<E> forActivity(Activity activity) {
        startIf(e -> e.getBrain().getActiveNonCoreActivity().orElse(null) == activity);
        stopIf(e -> e.getBrain().getActiveNonCoreActivity().orElse(null) != activity);
        return this;
    }

    public Task<E> onStop(Consumer<E> consumer) {
        onStop = onStop.andThen(consumer);
        return this;
    }

    public Task<E> maxDuration(ToIntFunction<E> provider) {
        durationProvider = provider;
        return this;
    }

    public Task<E> maxDuration(int duration) {
        durationProvider = e -> duration;
        return this;
    }

    public Task<E> maxDuration(int min, int max) {
        durationProvider =e -> e.getRandom().nextInt(min, max);
        return this;
    }

    public Task<E> cooldown(ToIntFunction<E> provider) {
        cooldownProvider = provider;
        return this;
    }

    public Task<E> cooldown(int duration) {
        cooldownProvider = e -> duration;
        return this;
    }

    public Task<E> cooldown(int min, int max) {
        cooldownProvider =e -> e.getRandom().nextInt(min, max);
        return this;
    }

    public Pair<Integer, Task<E>> defaultPriority() {
        return Pair.of(1, this);
    }

    public Pair<Integer, Task<E>> priority(int priority) {
        return Pair.of(priority, this);
    }

    public Task<E> requiresMemories(Map<MemoryModuleType<?>, MemoryStatus> memories) {
        entryCondition.putAll(memories);
        return this;
    }

    public Task<E> requiresMemoriesAndEraseOnStop(Map<MemoryModuleType<?>, MemoryStatus> memories) {
        entryCondition.putAll(memories);
        eraseMemoriesOnStop(memories.keySet());
        return this;
    }

    public Task<E> eraseMemoriesOnStop(Set<MemoryModuleType<?>> memories) {
        memoriesToEraseOnStop.addAll(memories);
        return this;
    }

    public Task<E> debug(String info) {
        debugInfo = info;
        return this;
    }

    @Override
    public @NotNull String debugString() {
        String timeLeft = level == null ? "?]" : endTimestamp - level.getGameTime() + "]";
        return getClass().getSimpleName() + " (" + debugInfo + ") ["
            + ((endTimestamp >= SharedConstants.TICKS_PER_GAME_DAY) ? ">day]" : timeLeft);
    }

    public String getDebugInfo() {
        return debugInfo;
    }
}
