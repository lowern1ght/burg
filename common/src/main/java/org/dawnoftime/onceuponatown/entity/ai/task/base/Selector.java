package org.dawnoftime.onceuponatown.entity.ai.task.base;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.GateBehavior;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Selector<E extends LivingEntity> extends Task<E> {
    private final ShufflingList<BehaviorControl<? super E>> behaviors = new ShufflingList<>();
    private final OrderPolicy orderPolicy;
    private final ChoosePolicy choosePolicy;

    public Selector(OrderPolicy orderPolicy, ChoosePolicy choosePolicy, List<Pair<Integer, ? extends BehaviorControl<? super E>>> behaviors) {
        super(new HashMap<>());
        this.orderPolicy = orderPolicy;
        this.choosePolicy = choosePolicy;
        behaviors.forEach(pair -> this.behaviors.add(pair.getSecond(), pair.getFirst()));
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        orderPolicy.apply(behaviors);
        choosePolicy.apply(behaviors.stream(), level, entity, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        behaviors.stream().filter((behaviorControl) -> behaviorControl.getStatus() == Status.RUNNING)
            .forEach((behaviorControl) -> behaviorControl.tickOrStop(level, entity, gameTime));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime)
            && behaviors.stream().anyMatch((behaviorControl) -> behaviorControl.getStatus() == Status.RUNNING);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Status.RUNNING)
            .forEach(behavior -> behavior.doStop(level, entity, gameTime));
    }

    @Override
    public @NotNull String debugString() {
        StringJoiner joiner = new StringJoiner("  |  ");
        behaviors.stream()
            .filter(behaviorControl -> behaviorControl.getStatus() == Status.RUNNING)
            .map(BehaviorControl::debugString)
            .forEach(joiner::add);
        return orderPolicy.debugName + " " + choosePolicy.debugName + " Select(" +  getDebugInfo() + ") [" + joiner + "]";
    }

    public enum OrderPolicy {
        ORDERED(shufflingList -> {
        }, "ORD"),
        SHUFFLED(ShufflingList::shuffle, "RAND");

        private final Consumer<ShufflingList<?>> consumer;
        private final String debugName;

        OrderPolicy(Consumer<ShufflingList<?>> consumer, String debugName) {
            this.consumer = consumer;
            this.debugName = debugName;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public enum ChoosePolicy {
        FIND_FIRST("FF") {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter((behaviorControl) -> behaviorControl.getStatus() == Status.STOPPED).filter((behaviorControl) -> behaviorControl.tryStart(level, owner, gameTime)).findFirst();
            }
        },
        TRY_ALL("TA") {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter((behaviorControl) -> behaviorControl.getStatus() == Status.STOPPED).forEach((behaviorControl) -> behaviorControl.tryStart(level, owner, gameTime));
            }
        };

        private final String debugName;

        ChoosePolicy(String debugName) {
            this.debugName = debugName;
        }

        public abstract <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime);
    }
}
