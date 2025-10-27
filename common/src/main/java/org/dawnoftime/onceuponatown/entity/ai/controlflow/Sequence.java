package org.dawnoftime.onceuponatown.entity.ai.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class Sequence<E extends LivingEntity> extends Task<E> {
    private final List<BehaviorControl<? super E>> children; // Behaviors to run one after another
    private int step;

    public Sequence(Sequence.Builder<E> builder) {
        super(builder);
        this.children = builder.children;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        step = 0;
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        var behavior = children.get(step);
        if (behavior.getStatus() == Status.STOPPED) {
            if (!behavior.tryStart(level, entity, gameTime)) {
                doStop(level, entity, gameTime);
            }
        }
        if (behavior.getStatus() == Status.RUNNING) {
            behavior.tickOrStop(level, entity, gameTime);
            if (behavior.getStatus() == Status.STOPPED) {
                ++step;
                if (step >= children.size()) {
                    doStop(level, entity, gameTime);
                }
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
        StringJoiner joiner = new StringJoiner(" | ");
        children.stream()
            .map(bc -> bc.getStatus() == Status.RUNNING ? ">> " + bc.debugString() : bc.debugString())
            .forEach(joiner::add);
        return getClass().getSimpleName() + "(" + getName() + ")[" + joiner + "]";
    }

    @Override
    public @NotNull String fullDebugString() {
        return debugString() + "<" + getTimeRemaining() + "," + step + 1 + "/" + children.size() + ">";
    }

    public static class Builder<E extends LivingEntity> extends Task.Builder<E> {
        private final List<BehaviorControl<? super E>> children = new ArrayList<>();

        public Builder(String name) {
            super(name);
        }

        public Builder<E> step(BehaviorControl<? super E> step) {
            children.add(step);
            return this;
        }

        @Override
        public Task<E> assemble() {
            return new Sequence<>(this);
        }
    }
}
