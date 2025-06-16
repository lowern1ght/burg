package org.dawnoftime.onceuponatown.entity.ai.task.base;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;
import java.util.function.Consumer;

public class Tasks {
    public static <E extends LivingEntity> Task<E> run(Consumer<E> consumer) {
        return new OneShotTask<E>().onStart(consumer);
    }

    @SafeVarargs
    public static <E extends LivingEntity> Selector<E> select(
        Selector.OrderPolicy orderPolicy,
        Selector.ChoosePolicy choosePolicy,
        Pair<Integer, ? extends BehaviorControl<? super E>>... behaviors
    ) {
        return new Selector<E>(orderPolicy, choosePolicy, ImmutableList.copyOf(behaviors));
    }

    @SafeVarargs
    public static <E extends LivingEntity> Sequence<E> sequence(boolean acceptsFailure, BehaviorControl<? super E>... behaviors) {
        return new Sequence<E>(ImmutableList.copyOf(behaviors), acceptsFailure);
    }

    public static <E extends LivingEntity> Repeater<E> repeat(boolean acceptsFailure, BehaviorControl<? super E> behavior) {
        return new Repeater<E>(behavior, acceptsFailure);
    }

    public static <E extends LivingEntity> Selector<E> select(
        Selector.OrderPolicy orderPolicy,
        Selector.ChoosePolicy choosePolicy,
        List<Pair<Integer, ? extends BehaviorControl<? super E>>> behaviors
    ) {
        return new Selector<E>(orderPolicy, choosePolicy, behaviors);
    }

    public static <E extends LivingEntity> Sequence<E> sequence(boolean acceptsFailure, List<BehaviorControl<? super E>> behaviors) {
        return new Sequence<E>(behaviors, acceptsFailure);
    }
}
