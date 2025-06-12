package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

import java.util.List;
import java.util.function.Consumer;

public class Control {
    public static <E extends LivingEntity> EasyBehavior<E> run(Consumer<E> consumer) {
        return new EasyOneShot<E>().onStart(consumer);
    }

    public static <E extends LivingEntity> Selector<E> select(
        Selector.OrderPolicy orderPolicy,
        Selector.ChoosePolicy choosePolicy,
        List<Pair<Integer, ? extends BehaviorControl<? super E>>> behaviors
    ) {
        return new Selector<>(orderPolicy, choosePolicy, behaviors);
    }

    @SafeVarargs
    public static <E extends LivingEntity> Selector<E> select(
        Selector.OrderPolicy orderPolicy,
        Selector.ChoosePolicy choosePolicy,
        Pair<Integer, ? extends BehaviorControl<? super E>>... behaviors
    ) {
        return new Selector<>(orderPolicy, choosePolicy, ImmutableList.copyOf(behaviors));
    }

    public static <E extends LivingEntity> Sequence<E> sequence(boolean acceptsFailure, List<BehaviorControl<? super E>> behaviors) {
        return new Sequence<>(behaviors, acceptsFailure);
    }

    @SafeVarargs
    public static <E extends LivingEntity> Sequence<E> sequence(boolean acceptsFailure, BehaviorControl<? super E>... behaviors) {
        return new Sequence<>(ImmutableList.copyOf(behaviors), acceptsFailure);
    }

    public static <E extends LivingEntity> Repeater<E> repeat(boolean acceptsFailure, BehaviorControl<? super E> behavior) {
        return new Repeater<>(behavior, acceptsFailure);
    }
}
