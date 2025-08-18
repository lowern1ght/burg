package org.dawnoftime.onceuponatown.entity.ai.task;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.dawnoftime.onceuponatown.entity.ai.task.base.SingleTask;

import java.util.function.Function;

public class SetMemory<E extends LivingEntity> extends SingleTask<E> {
    public <U> SetMemory(MemoryModuleType<U> memoryType, Function<E, U> valueProvider) {
        onStart(e -> e.getBrain().setMemory(memoryType, valueProvider.apply(e)));
    }

    public <U> SetMemory(MemoryModuleType<U> memoryType, Function<E, U> valueProvider, long timeToLive) {
        onStart(e -> e.getBrain().setMemoryWithExpiry(memoryType, valueProvider.apply(e), timeToLive));
    }
}
