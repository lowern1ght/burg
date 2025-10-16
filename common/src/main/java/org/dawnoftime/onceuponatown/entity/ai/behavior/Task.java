package org.dawnoftime.onceuponatown.entity.ai.behavior;

import com.mojang.datafixers.util.Pair;
import com.sun.jna.platform.win32.OaIdl;
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
    private final Set<MemoryModuleType<?>> memoriesToEraseOnStop;
    private final Predicate<E> startPredicate;
    private final Predicate<E> stopPredicate;
    private final Consumer<E> onStart;
    private final Consumer<E> onTick;
    private final Consumer<E> onStop;
    private final ToIntFunction<E> durationProvider;
    private final ToIntFunction<E> cooldownProvider;
    private final String name;
    private long cooldownEnd = 0L;
    private ServerLevel level;

    public Task(Builder<E> builder) {
        super(builder.entryCondition);
        this.memoriesToEraseOnStop = builder.memoriesToEraseOnStop;
        this.startPredicate = builder.startPredicate;
        this.stopPredicate = builder.stopPredicate;
        this.onStart = builder.onStart;
        this.onTick = builder.onTick;
        this.onStop = builder.onStop;
        this.durationProvider = builder.durationProvider;
        this.cooldownProvider = builder.cooldownProvider;
        this.name = builder.name;
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
        memoriesToEraseOnStop.forEach(entity.getBrain()::eraseMemory);
        onStop.accept(entity);
        cooldownEnd = gameTime + cooldownProvider.applyAsInt(entity);
    }

    public final Pair<Integer, Task<E>> defaultPriority() {
        return Pair.of(1, this);
    }

    public final Pair<Integer, Task<E>> priority(int priority) {
        return Pair.of(priority, this);
    }

    @Override
    public @NotNull String debugString() {
        return getClass().getSimpleName() + "(" + name + ")";
    }

    public @NotNull String fullDebugString() {
        return debugString() + "<" + getTimeRemaining() + ">";
    }

    public @NotNull String getTimeRemaining() {
        String remainingTime;
        float timeLeft = endTimestamp - level.getGameTime();
        if (level == null) {
            remainingTime = "?";
        } else if (timeLeft >= Integer.MAX_VALUE) {
            remainingTime = "inf";
        } else if (timeLeft >= SharedConstants.TICKS_PER_GAME_DAY) {
            remainingTime = ">day";
        } else {
            remainingTime = String.valueOf(timeLeft);
        }
        return remainingTime;
    }

    public final String getName() {
        return name;
    }

    public static <E extends LivingEntity> Builder<E> builder(String name) {
        return new Builder<>(name);
    }

    public static class Builder<E extends LivingEntity> {
        private final Map<MemoryModuleType<?>, MemoryStatus> entryCondition = new HashMap<>();
        private final Set<MemoryModuleType<?>> memoriesToEraseOnStop = new HashSet<>();
        private Predicate<E> startPredicate = entity -> true;
        private Predicate<E> stopPredicate = entity -> false;
        private Consumer<E> onStart = entity -> {};
        private Consumer<E> onTick = entity -> {};
        private Consumer<E> onStop = entity -> {};
        private ToIntFunction<E> durationProvider = entity -> Integer.MAX_VALUE; // Behaviors run forever by default
        private ToIntFunction<E> cooldownProvider = entity -> 0;
        private final String name;

        public Builder(String name) {
            this.name = name;
        }

        public Task<E> assemble() {
            return new Task<>(this);
        }

        public final Builder<E> requiresMemories(Map<MemoryModuleType<?>, MemoryStatus> memories) {
            entryCondition.putAll(memories);
            return this;
        }

        public final Builder<E> requiresMemoriesAndEraseOnStop(Map<MemoryModuleType<?>, MemoryStatus> memories) {
            entryCondition.putAll(memories);
            eraseMemoriesOnStop(memories.keySet());
            return this;
        }

        public final Builder<E> eraseMemoriesOnStop(Set<MemoryModuleType<?>> memories) {
            memoriesToEraseOnStop.addAll(memories);
            return this;
        }

        public final Builder<E> startIf(Predicate<E> predicate) {
            startPredicate = startPredicate.and(predicate);
            return this;
        }

        public final Builder<E> stopIf(Predicate<E> predicate) {
            stopPredicate = stopPredicate.or(predicate);
            return this;
        }

        public final Builder<E> onStart(Consumer<E> consumer) {
            onStart = onStart.andThen(consumer);
            return this;
        }

        public final Builder<E> onTick(Consumer<E> consumer) {
            onTick = onTick.andThen(consumer);
            return this;
        }

        public final Builder<E> onStop(Consumer<E> consumer) {
            onStop = onStop.andThen(consumer);
            return this;
        }

        public final Builder<E> maxDuration(ToIntFunction<E> provider) {
            durationProvider = provider;
            return this;
        }

        public final Builder<E> maxDuration(int duration) {
            durationProvider = e -> duration;
            return this;
        }

        public final Builder<E> maxDuration(int min, int max) {
            durationProvider =e -> e.getRandom().nextInt(min, max);
            return this;
        }

        public final Builder<E> cooldown(ToIntFunction<E> provider) {
            cooldownProvider = provider;
            return this;
        }

        public final Builder<E> cooldown(int duration) {
            cooldownProvider = e -> duration;
            return this;
        }

        public final Builder<E> cooldown(int min, int max) {
            cooldownProvider =e -> e.getRandom().nextInt(min, max);
            return this;
        }

        public final Builder<E> restrictPosition(Function<E, BlockPos> position, int closeEnoughDistance) {
            startIf(npc -> position.apply(npc).distManhattan(npc.blockPosition()) <= closeEnoughDistance);
            stopIf(npc -> position.apply(npc).distManhattan(npc.blockPosition()) > closeEnoughDistance);
            return this;
        }

        public final Builder<E> probability(float percentChance) {
            startIf(e -> (e.getRandom().nextFloat() < percentChance));
            return this;
        }

        public final Builder<E> probability(Function<E, Float> probabilityProvider) {
            startIf(e -> e.getRandom().nextFloat() < probabilityProvider.apply(e));
            return this;
        }

        /**
         * Stops this task if the brain is not running the required activity
         **/
        public final Builder<E> forActivity(@NotNull Activity activity) {
            startIf(e -> e.getBrain().getActiveNonCoreActivity().orElse(null) == activity);
            stopIf(e -> e.getBrain().getActiveNonCoreActivity().orElse(null) != activity);
            return this;
        }
    }
}
