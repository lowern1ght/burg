package org.dawnoftime.onceuponatown.entity.ai.controlflow;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

public class SingleTask<E extends LivingEntity> extends Task<E> {
    private SingleTask(SingleTask.Builder<E> builder) {
        super(builder);
    }

    public SingleTask(Task.Builder<E> builder, Consumer<E> consumer) {
        super(builder.onStart(consumer));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return false;
    }

    public static class Builder<E extends LivingEntity> extends Task.Builder<E> {
        public Builder(String name, Consumer<E> consumer) {
            super(name);
            onStart(consumer);
        }

        @Override
        public Task<E> assemble() {
            return new SingleTask<>(this);
        }
    }
}
