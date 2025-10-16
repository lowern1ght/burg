package org.dawnoftime.onceuponatown.entity.ai.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class Repeater<E extends LivingEntity> extends Task<E> {
    private final BehaviorControl<? super E> child; // Child behavior to repeat
    private final ToIntFunction<E> repeatsProvider;
    private final Predicate<E> repeatPredicate;
    private int remainingRepeats;

    public Repeater(Builder<E> builder) {
        super(builder);
        this.child = builder.child;
        this.repeatsProvider = builder.repeatsProvider;
        this.repeatPredicate = builder.repeatPredicate;
    }

    @Override
    protected final void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        remainingRepeats = repeatsProvider.applyAsInt(entity);
    }

    @Override
    protected final void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        if (child.getStatus() == Status.STOPPED) {
            if (repeatPredicate.test(entity) && !child.tryStart(level, entity, gameTime)) {
                doStop(level, entity, gameTime);
            } else {
                --remainingRepeats;
            }
        }
        if (child.getStatus() == Status.RUNNING) {
            child.tickOrStop(level, entity, gameTime);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime)
            && (child.getStatus() == Status.RUNNING || remainingRepeats > 0);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        if (child.getStatus() == Status.RUNNING) {
            child.doStop(level, entity, gameTime);
        }
    }

    @Override
    public @NotNull String debugString() {
        return getClass().getSimpleName() + "(" + getName() + ")[" + child.debugString() + "]";
    }

    @Override
    public @NotNull String fullDebugString() {
        return debugString() + "<" + getTimeRemaining() + ", " + remainingRepeats + ">";
    }

    public static class Builder<E extends LivingEntity> extends Task.Builder<E> {
        private final BehaviorControl<? super E> child;
        private ToIntFunction<E> repeatsProvider = entity -> Integer.MAX_VALUE;
        private Predicate<E> repeatPredicate = entity -> true;

        public Builder(String name, BehaviorControl<? super E> child) {
            super(name);
            this.child = child;
        }

        public Repeater.Builder<E> nTimes(ToIntFunction<E> provider) {
            repeatsProvider = provider;
            return this;
        }

        public Repeater.Builder<E> nTimes(int times) {
            return nTimes(entity -> times);
        }

        public Repeater.Builder<E> repeatIf(Predicate<E> predicate) {
            repeatPredicate = predicate;
            return this;
        }

        @Override
        public Task<E> assemble() {
            return new Repeater<>(this);
        }
    }
}
