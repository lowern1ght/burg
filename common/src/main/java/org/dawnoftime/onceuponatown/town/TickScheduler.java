package org.dawnoftime.onceuponatown.town;

import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class TickScheduler {
    private static final List<ScheduledTask> TASKS = new ArrayList<>();

    static void schedule(Runnable action, int delayTicks) {
        TASKS.add(new ScheduledTask(action, delayTicks));
    }

    static void tick(ServerLevel level) {
        Iterator<ScheduledTask> it = TASKS.iterator();
        while (it.hasNext()) {
            ScheduledTask task = it.next();
            task.ticksLeft--;
            if (task.ticksLeft <= 0) {
                task.action.run();
                it.remove();
            }
        }
    }

    private static class ScheduledTask {
        private final Runnable action;
        private int ticksLeft;

        private ScheduledTask(Runnable action, int ticksLeft) {
            this.ticksLeft = ticksLeft;
            this.action = action;
        }
    }
}
