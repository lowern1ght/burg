package org.dawnoftime.onceuponatown.entity.ai.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;

public class NpcPanic extends Task<Npc> {
    public NpcPanic(Task.Builder<Npc> builder) {
        super(builder);
    }

    public NpcPanic(String name) {
        super(Task.<Npc>builder(name));
    }

    @Override
    protected void start(ServerLevel level, Npc npc, long gameTime) {
        super.start(level, npc, gameTime);
        if (isHurt(npc) || hasHostile(npc)) {
            Brain<?> brain = npc.getBrain();
            // TODO or maybe erase these memories when other activities are stopped ?
            if (!brain.isActive(Activity.PANIC)) {
                brain.eraseMemory(MemoryModuleType.PATH);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                brain.eraseMemory(MemoryModuleType.BREED_TARGET);
                brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
            }
            brain.setActiveActivityIfPossible(Activity.PANIC);
        }
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Npc npc, long gameTime) {
        super.canStillUse(level, npc, gameTime);
        return isHurt(npc) || hasHostile(npc);
    }

    @Override
    protected void tick(ServerLevel level, Npc npc, long gameTime) {
        super.tick(level, npc, gameTime);
        //if (gameTime % 100L == 0L) {
            //npc.spawnGolemIfNeeded(level, gameTime, 3);
        //}
    }

    public static boolean hasHostile(Npc entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE);
    }

    public static boolean isHurt(Npc entity) {
        return entity.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY);
    }
}
