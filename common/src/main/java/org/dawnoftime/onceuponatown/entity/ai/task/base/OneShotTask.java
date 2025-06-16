package org.dawnoftime.onceuponatown.entity.ai.task.base;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class OneShotTask<E extends LivingEntity> extends Task<E> {
    public OneShotTask(@NotNull Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        super(entryCondition);
    }

    public OneShotTask() {
        this(new HashMap<>());
    }

    public static <E extends LivingEntity> Task<E> run(Consumer<E> consumer) {
        return new OneShotTask<E>().onStart(consumer);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return false;
    }

    @Override
    public @NotNull String debugString() {
        return "OS " + super.debugString();
    }
}
