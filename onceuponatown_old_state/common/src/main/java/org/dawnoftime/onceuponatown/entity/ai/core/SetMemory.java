package org.dawnoftime.onceuponatown.entity.ai.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.SingleTask;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;

import java.util.function.Function;

public class SetMemory<E extends LivingEntity> extends SingleTask<E> {
    public <U> SetMemory(Task.Builder<E> builder, MemoryModuleType<U> memoryType, Function<E, U> valueProvider) {
        super(builder, e -> e.getBrain().setMemory(memoryType, valueProvider.apply(e)));
    }

    public <U> SetMemory(Task.Builder<E> builder, MemoryModuleType<U> memoryType, Function<E, U> valueProvider, long timeToLive) {
        super(builder, e -> e.getBrain().setMemoryWithExpiry(memoryType, valueProvider.apply(e), timeToLive));
    }

    public <U> SetMemory(String name, MemoryModuleType<U> memoryType, Function<E, U> valueProvider) {
        super(Task.<E>builder(name), e -> e.getBrain().setMemory(memoryType, valueProvider.apply(e)));
    }

    public <U> SetMemory(String name, MemoryModuleType<U> memoryType, Function<E, U> valueProvider, long timeToLive) {
        super(Task.<E>builder(name), e -> e.getBrain().setMemoryWithExpiry(memoryType, valueProvider.apply(e), timeToLive));
    }
}
