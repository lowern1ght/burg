package org.dawnoftime.onceuponatown.entity.ai.task.base;

import com.google.common.collect.ImmutableList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;

public class Sequence<E extends LivingEntity> extends Task<E> {
    private final boolean strict; // If strict, the sequence will stop if a child behavior fails to start
    private final List<BehaviorControl<? super E>> children; // Behaviors to run one after another
    private int step;

    @SafeVarargs
    public Sequence(boolean strict, BehaviorControl<? super E>... behaviors) {
        this.children = ImmutableList.copyOf(behaviors);
        this.strict = strict;
    }

    @SafeVarargs
    public static <E extends LivingEntity> Sequence<E> of(BehaviorControl<? super E>... behaviors) {
        return new Sequence<>(true, behaviors);
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        step = 0;
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        // Improve this, some ticks will not tick children
        super.tick(level, entity, gameTime);
        var behavior = children.get(step);
        if (behavior.getStatus() == Status.STOPPED) {
            if (!behavior.tryStart(level, entity, gameTime)) {
                step = strict ? children.size() : step + 1;
            }
        }
        if (behavior.getStatus() == Status.RUNNING) {
            behavior.tickOrStop(level, entity, gameTime);
            if (behavior.getStatus() == Status.STOPPED) {
                ++step;
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime) && step < children.size();
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        children.stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .forEach(behavior -> behavior.doStop(level, entity, gameTime));
    }

    @Override
    public @NotNull String debugString() {
        StringJoiner joiner = new StringJoiner("  |  ");
        children.stream()
            .map(bc -> bc.getStatus() == Status.RUNNING ? ">>> " + bc.debugString() : bc.debugString())
            .forEach(joiner::add);
        return "Sequence(" + getDebugInfo() + ") [" + step + "] [" + joiner + "]";
    }
}
