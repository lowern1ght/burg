package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class Sequence<E extends LivingEntity> extends EasyBehavior<E> {
    private final List<BehaviorControl<? super E>> behaviors; // Behaviors to run, one after another
    /**
     * True : if a child behavior fails to start, the sequence stops <br>
     * False : if a child behavior fails to start, the sequence tries to start the next behavior
     */
    private final boolean acceptsFailure;
    private int step;

    public Sequence(List<BehaviorControl<? super E>> behaviors, boolean acceptsFailure) {
        super(new HashMap<>());
        this.behaviors = behaviors;
        this.acceptsFailure = acceptsFailure;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        step = 0;
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        var behavior = behaviors.get(step);
        if (behavior.getStatus() == Status.STOPPED) {
            if (!behavior.tryStart(level, entity, gameTime)) {
                step = acceptsFailure ? step + 1 : behaviors.size();
            }
        } else {
            behavior.tickOrStop(level, entity, gameTime);
            if (behavior.getStatus() == Status.STOPPED) {
                ++step;
            }
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime) && step < behaviors.size();
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .forEach(behavior -> behavior.doStop(level, entity, gameTime));
    }

    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Behavior.Status.RUNNING)
            .collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }
}
