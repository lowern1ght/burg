package org.dawnoftime.onceuponatown.registry;

import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

import java.util.function.Supplier;

public abstract class ScheduleRegistry {
    public static ScheduleRegistry REGISTRY;

    public final Supplier<Schedule> DEFAULT_WORKER = register("default_worker",
        () -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(2000, Activity.WORK)
            .changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(11000, Activity.IDLE)
            .changeActivityAt(12000, Activity.REST)
            .build()
    );

    public final Supplier<Schedule> DEFAULT_UNEMPLOYED = register("unemployed",
        () -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(11000, Activity.IDLE)
            .changeActivityAt(12000, Activity.REST)
            .build()
    );

    public final Supplier<Schedule> DEFAULT_BABY = register("default_baby",
        () -> new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(3000, Activity.PLAY)
            .changeActivityAt(6000, Activity.IDLE)
            .changeActivityAt(10000, Activity.PLAY)
            .changeActivityAt(12000, Activity.REST)
            .build()
    );

    public abstract Supplier<Schedule> register(final String name, final Supplier<Schedule> scheduleSupplier);
}