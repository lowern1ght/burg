package org.dawnoftime.onceuponatown.entity.ai.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ShufflingList;
import org.jetbrains.annotations.NotNull;

import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Selector<E extends LivingEntity> extends Task<E> {
    private final ShufflingList<BehaviorControl<? super E>> children;
    private final OrderPolicy orderPolicy;
    private final ChoosePolicy choosePolicy;

    public Selector(Selector.Builder<E> builder) {
        super(builder);
        this.children = builder.children;
        this.orderPolicy = builder.orderPolicy;
        this.choosePolicy = builder.choosePolicy;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        super.start(level, entity, gameTime);
        orderPolicy.apply(children);
        choosePolicy.apply(children.stream(), level, entity, gameTime);
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        super.tick(level, entity, gameTime);
        children.stream().filter((behaviorControl) -> behaviorControl.getStatus() == Status.RUNNING)
            .forEach((behaviorControl) -> behaviorControl.tickOrStop(level, entity, gameTime));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return super.canStillUse(level, entity, gameTime)
            && children.stream().anyMatch((behaviorControl) -> behaviorControl.getStatus() == Status.RUNNING);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        children.stream()
            .filter(behavior -> behavior.getStatus() == Status.RUNNING)
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
        return debugString() + "<" + getTimeRemaining() + ", " +
            children.stream()
                .filter(behaviorControl -> behaviorControl.getStatus() == Status.RUNNING)
                .toList()
                .size() + "/" + children.stream().toList().size() + ", "
                + orderPolicy.name() + ", " + choosePolicy.name() + ">";
    }

    public enum OrderPolicy {
        ORDERED(shufflingList -> {}),
        SHUFFLED(ShufflingList::shuffle);

        private final Consumer<ShufflingList<?>> consumer;

        OrderPolicy(Consumer<ShufflingList<?>> consumer) {
            this.consumer = consumer;
        }

        public void apply(ShufflingList<?> list) {
            this.consumer.accept(list);
        }
    }

    public enum ChoosePolicy {
        FIND_FIRST {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter((behaviorControl) -> behaviorControl.getStatus() == Status.STOPPED).filter((behaviorControl) -> behaviorControl.tryStart(level, owner, gameTime)).findFirst();
            }
        },
        TRY_ALL {
            @Override
            public <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime) {
                behaviors.filter((behaviorControl) -> behaviorControl.getStatus() == Status.STOPPED).forEach((behaviorControl) -> behaviorControl.tryStart(level, owner, gameTime));
            }
        };

        public abstract <E extends LivingEntity> void apply(Stream<BehaviorControl<? super E>> behaviors, ServerLevel level, E owner, long gameTime);
    }

    public static class Builder<E extends LivingEntity> extends Task.Builder<E> {
        private final ShufflingList<BehaviorControl<? super E>> children = new ShufflingList<>();
        private final OrderPolicy orderPolicy;
        private final ChoosePolicy choosePolicy;

        public Builder(String name, OrderPolicy orderPolicy, ChoosePolicy choosePolicy) {
            super(name);
            this.orderPolicy = orderPolicy;
            this.choosePolicy = choosePolicy;
        }

        public Selector.Builder<E> option(int weight, BehaviorControl<? super E> option) {
            children.add(option, weight);
            return this;
        }

        @Override
        public Task<E> assemble() {
            return new Selector<>(this);
        }
    }
}
