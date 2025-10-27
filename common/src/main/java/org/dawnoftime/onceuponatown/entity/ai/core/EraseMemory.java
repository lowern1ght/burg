package org.dawnoftime.onceuponatown.entity.ai.core;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.SingleTask;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;

public class EraseMemory<E extends LivingEntity> extends SingleTask<E> {
    public <U> EraseMemory(Task.Builder<E> builder, MemoryModuleType<U> memoryType) {
        super(builder, e -> e.getBrain().eraseMemory(memoryType));
    }

    public <U> EraseMemory(String name, MemoryModuleType<U> memoryType) {
        this(Task.<E>builder(name), memoryType);
    }
}
