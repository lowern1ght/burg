package org.dawnoftime.onceuponatown.registry;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

import java.util.Optional;
import java.util.function.Supplier;

public abstract class MemoryModuleTypeRegistry {
    public static MemoryModuleTypeRegistry REGISTRY;

    public final Supplier<MemoryModuleType<GlobalPos>> WORK_POS = register("work_pos",
        () -> new MemoryModuleType<>(Optional.of(GlobalPos.CODEC))
    );

    public final Supplier<MemoryModuleType<GlobalPos>> WORK_TARGET = register("work_target",
        () -> new MemoryModuleType<>(Optional.of(GlobalPos.CODEC))
    );

    public abstract <U> Supplier<MemoryModuleType<U>> register(final String name, final Supplier<MemoryModuleType<U>> memoryModuleTypeSupplier);
}