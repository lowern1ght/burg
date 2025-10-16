package org.dawnoftime.onceuponatown.entity.ai.behavior;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.dawnoftime.onceuponatown.entity.Npc;

import java.util.function.Consumer;

public class Tasks<E extends LivingEntity> {
    public Task.Builder<E> ticking(String name) {
        return new Task.Builder<>(name);
    }

    public static <E extends LivingEntity> Task.Builder<E> tickingS(String name) {
        return new Task.Builder<>(name);
    }

    public SingleTask.Builder<E> single(String name, Consumer<E> consumer) {
        return new SingleTask.Builder<>(name, consumer);
    }

    public static <E extends LivingEntity> SingleTask.Builder<E> singleS(String name, Consumer<E> consumer) {
        return new SingleTask.Builder<>(name, consumer);
    }

    public Sequence.Builder<E> sequence(String name) {
        return new Sequence.Builder<>(name);
    }

    public static <E extends LivingEntity> Sequence.Builder<E> sequenceS(String name) {
        return new Sequence.Builder<>(name);
    }

    public Selector.Builder<E> firstValid(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.ORDERED, Selector.ChoosePolicy.FIND_FIRST);
    }

    public static <E extends LivingEntity> Selector.Builder<E> firstValidS(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.ORDERED, Selector.ChoosePolicy.FIND_FIRST);
    }

    public Selector.Builder<E> tryAll(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.ORDERED, Selector.ChoosePolicy.TRY_ALL);
    }

    public static <E extends LivingEntity> Selector.Builder<E> tryAllS(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.ORDERED, Selector.ChoosePolicy.TRY_ALL);
    }

    public Selector.Builder<E> oneRandom(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.SHUFFLED, Selector.ChoosePolicy.FIND_FIRST);
    }

    public static <E extends LivingEntity> Selector.Builder<E> oneRandomS(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.SHUFFLED, Selector.ChoosePolicy.FIND_FIRST);
    }

    public Selector.Builder<E> allRandom(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.SHUFFLED, Selector.ChoosePolicy.TRY_ALL);
    }

    public static <E extends LivingEntity> Selector.Builder<E> allRandomS(String name) {
        return new Selector.Builder<E>(name, Selector.OrderPolicy.SHUFFLED, Selector.ChoosePolicy.TRY_ALL);
    }

    public Repeater.Builder<E> repeat(String name, BehaviorControl<? super E> behavior) {
        return new Repeater.Builder<>(name, behavior);
    }

    public static <E extends LivingEntity> Repeater.Builder<E> repeatS(String name, BehaviorControl<? super E> behavior) {
        return new Repeater.Builder<>(name, behavior);
    }
}
