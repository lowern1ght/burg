package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.HashMap;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public class Repeater<E extends LivingEntity> extends EasyBehavior<E> {
    private final BehaviorControl<? super E> behavior; // Child behavior to repeat
    private final boolean acceptsFailure;
    protected Predicate<E> repeatPredicate = entity -> true;
    private ToIntFunction<E> repeatsProvider = entity -> Integer.MAX_VALUE;
    private int remainingRepeats;

    public Repeater(BehaviorControl<? super E> behavior, boolean acceptsFailure) {
        super(new HashMap<>());
        this.behavior = behavior;
        this.acceptsFailure = acceptsFailure;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E owner) {
        return behavior.tryStart(level, owner, level.getGameTime());
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        remainingRepeats = repeatsProvider.applyAsInt(entity);
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        if (behavior.getStatus() == Status.RUNNING) {
            behavior.tickOrStop(level, entity, gameTime);
        } else if (remainingRepeats > 0 && repeatPredicate.test(entity) && (behavior.tryStart(level, entity, gameTime) || acceptsFailure)) {
            --remainingRepeats;
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime)
            && (behavior.getStatus() == Status.RUNNING || remainingRepeats > 0);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        if (behavior.getStatus() == Status.RUNNING) {
            behavior.doStop(level, entity, gameTime);
        }
    }

    public Repeater<E> repeatIf(Predicate<E> predicate) {
        repeatPredicate = predicate;
        return this;
    }

    public Repeater<E> nTimes(int times) {
        return nTimes(entity -> times);
    }

    public Repeater<E> nTimes(ToIntFunction<E> provider) {
        repeatsProvider = provider;
        return this;
    }

    public String toString() {
        return "(" + this.getClass().getSimpleName() + " " + remainingRepeats + " remaining): " + behavior.getClass().getSimpleName();
    }
}
