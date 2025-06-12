package org.dawnoftime.onceuponatown.entity.ai.behavior.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class EasyOneShot<E extends LivingEntity> extends EasyBehavior<E> {
    public EasyOneShot(@NotNull Map<MemoryModuleType<?>, MemoryStatus> entryCondition) {
        super(entryCondition);
    }

    public EasyOneShot() {
        this(new HashMap<>());
    }

    public static <E extends LivingEntity> EasyBehavior<E> run(Consumer<E> consumer) {
        return new EasyOneShot<E>().onStart(consumer);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return false;
    }
}
