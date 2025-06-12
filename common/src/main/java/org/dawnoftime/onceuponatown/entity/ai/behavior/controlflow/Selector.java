package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import com.mojang.datafixers.util.Pair;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.ShufflingList;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Selector<E extends LivingEntity> extends EasyBehavior<E> {
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
            && behaviors.stream().noneMatch((behaviorControl) -> behaviorControl.getStatus() == Status.RUNNING);
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Status.RUNNING)
            .forEach(behavior -> behavior.doStop(level, entity, gameTime));
    }

    public String toString() {
        Set<? extends BehaviorControl<? super E>> set = behaviors.stream()
            .filter(behavior -> behavior.getStatus() == Status.RUNNING)
            .collect(Collectors.toSet());
        return "(" + this.getClass().getSimpleName() + "): " + set;
    }

    public enum OrderPolicy {
        ORDERED(shufflingList -> {
        }),
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
}
