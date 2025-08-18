package org.dawnoftime.onceuponatown.entity.ai.task;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.dawnoftime.onceuponatown.entity.ai.task.base.SingleTask;

public class EraseMemory<E extends LivingEntity> extends SingleTask<E> {
    public <U> EraseMemory(MemoryModuleType<U> memoryType) {
        onStart(e -> e.getBrain().eraseMemory(memoryType));
    }
}
