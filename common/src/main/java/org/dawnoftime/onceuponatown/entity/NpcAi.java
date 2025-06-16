package org.dawnoftime.onceuponatown.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import org.dawnoftime.onceuponatown.entity.ai.task.FindBed;
import org.dawnoftime.onceuponatown.entity.ai.task.NpcCalmDown;
import org.dawnoftime.onceuponatown.entity.ai.task.NpcPanic;
import org.dawnoftime.onceuponatown.entity.ai.task.base.Selector;
import org.dawnoftime.onceuponatown.entity.ai.task.base.Task;
import org.dawnoftime.onceuponatown.entity.ai.task.base.Tasks;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ScheduleRegistry;

import java.util.function.Predicate;

public class NpcAi {
    // TODO Fight package : set aggressive
    static final ImmutableList<MemoryModuleType<?>> MEMORIES = com.google.common.collect.ImmutableList.of(
        MemoryModuleType.HOME,
        MemoryModuleType.JOB_SITE,
        MemoryModuleType.POTENTIAL_JOB_SITE,
        MemoryModuleType.MEETING_POINT,
        MemoryModuleType.NEAREST_LIVING_ENTITIES,
        MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
        MemoryModuleType.VISIBLE_VILLAGER_BABIES,
        MemoryModuleType.NEAREST_PLAYERS,
        MemoryModuleType.NEAREST_VISIBLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER,
        MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM,
        MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS,
        MemoryModuleType.WALK_TARGET,
        MemoryModuleType.LOOK_TARGET,
        MemoryModuleType.INTERACTION_TARGET,
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.PATH,
        MemoryModuleType.DOORS_TO_CLOSE,
        MemoryModuleType.NEAREST_BED,
        MemoryModuleType.HURT_BY,
        MemoryModuleType.HURT_BY_ENTITY,
        MemoryModuleType.NEAREST_HOSTILE,
        MemoryModuleType.SECONDARY_JOB_SITE,
        MemoryModuleType.HIDING_PLACE,
        MemoryModuleType.HEARD_BELL_TIME,
        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
        MemoryModuleType.LAST_SLEPT,
        MemoryModuleType.LAST_WOKEN,
        MemoryModuleType.LAST_WORKED_AT_POI,
        MemoryModuleType.GOLEM_DETECTED_RECENTLY
    );
    static final ImmutableList<SensorType<? extends Sensor<? super Npc>>> SENSORS = com.google.common.collect.ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.NEAREST_ITEMS,
        SensorType.NEAREST_BED,
        SensorType.HURT_BY,
        SensorType.VILLAGER_HOSTILES,
        SensorType.VILLAGER_BABIES,
        SensorType.GOLEM_DETECTED
    );

    static void setupBrain(Brain<Npc> brain, Npc npc) {
        if (npc.isBaby()) {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_BABY.get());
            addPlayTasks(brain, 0.5F);
        } else if (npc.getProfession() != Profession.UNEMPLOYED) {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_WORKER.get());
            addWorkTasks(brain, npc.getProfession(), 0.5F);
        } else {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_WORKER.get());
            addWorkTasks(brain, npc.getProfession(), 0.5F);
            //brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_UNEMPLOYED.get());
        }

        addCoreTasks(brain, 0.5F);
        addIdleTasks(brain, 0.5F);
        addPanicTasks(brain, 0.5F);
        addRestTasks(brain, 0.5F);
        addMeetTasks(brain, 0.5F);
        addPreRaidTasks(brain, npc.getProfession(), 0.5F);
        addRaidTasks(brain, npc.getProfession(), 0.5F);
        addHideTasks(brain, npc.getProfession(), 0.5F);
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(npc.level().getDayTime(), npc.level().getGameTime());
    }

    private static void addCoreTasks(Brain<Npc> brain, float speedModifier) {
        Predicate<Mob> swimPredicate = mob -> mob.isInWater() && mob.getFluidHeight(FluidTags.WATER) > mob.getFluidJumpThreshold() || mob.isInLava();

        brain.addActivity(Activity.CORE,
            ImmutableList.of(
                Pair.of(0, new Task<Mob>().debug("Swim")
                    .startIf(swimPredicate)
                    .stopIf(swimPredicate.negate())
                    .onTick(mob -> {
                        if (mob.getRandom().nextFloat() < 0.8F) {
                            mob.getJumpControl().jump();
                        }
                    })
                ),
                Pair.of(0, InteractWithDoor.create()),
                Pair.of(0, new LookAtTargetSink(45, 90)),
                Pair.of(0, new NpcPanic()),
                Pair.of(0, WakeUp.create()),
                Pair.of(1, new MoveToTargetSink())
                //Pair.of(3, new TradeWithPlayer(speedModifier)),
                //Pair.of(5, GoToWantedItem.create(speedModifier, false, 4))
            )
        );
    }

    private static void addIdleTasks(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.IDLE,
            ImmutableList.of(
                Pair.of(
                    2,
                    new RunOne<>(
                        ImmutableList.of(
                            Pair.of(InteractWith.of(EntityRegistry.REGISTRY.NPC.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 2),
                            Pair.of(InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2), 1),
                            Pair.of(RandomStroll.stroll(speedModifier), 1),
                            Pair.of(SetWalkTargetFromLookTarget.create(speedModifier, 2), 1),
                            Pair.of(new DoNothing(30, 60), 1)
                        )
                    )
                ),
                Pair.of(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
                getFullLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
            )
        );
    }

    private static void addPanicTasks(Brain<Npc> brain, float speedModifier) {
        float f = speedModifier * 1.5F;
        brain.addActivity(Activity.PANIC,
            ImmutableList.of(
                Pair.of(0, NpcCalmDown.create()),
                Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
                Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
                Pair.of(3, RandomStroll.stroll(f)),
                getMinimalLookBehavior()
            )
        );
    }

    private static void addRestTasks(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.REST,
            ImmutableList.of(
                Pair.of(3, new SleepInBed()),
                Pair.of(
                    5,
                    new RunOne<>(
                        ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT),
                        ImmutableList.of(
                            Pair.of(FindBed.create(speedModifier), 1)
                            //Pair.of(InsideBrownianWalk.create(speedModifier), 4),
                            //Pair.of(new DoNothing(20, 40), 2)
                        )
                    )
                ),
                getMinimalLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
            )
        );
    }

    private static void addWorkTasks(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.WORK, ImmutableList.of(
            Pair.of(0, Tasks.select(Selector.OrderPolicy.ORDERED, Selector.ChoosePolicy.TRY_ALL,
                Pair.of(0, new Task<>()
                    .debug("Dummy1")
                    .maxDuration(40)
                )
                ,
                Pair.of(0, new Task<>()
                    .debug("Dummy2")
                    .maxDuration(80)
                )
                ,
                Pair.of(0, new Task<>()
                    .debug("Dummy3")
                    .maxDuration(120)
                )
            )),
            getMinimalLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static void addFightTasks(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.FIGHT,
            ImmutableList.of()
        );
    }

    private static void addMeetTasks(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.MEET,
            ImmutableList.of(
                getFullLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
            )
        );
    }

    private static void addPreRaidTasks(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.PRE_RAID,
            ImmutableList.of(
                getMinimalLookBehavior()
            )
        );
    }

    private static void addRaidTasks(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.RAID,
            ImmutableList.of(
                getMinimalLookBehavior()
            )
        );
    }

    private static void addHideTasks(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.HIDE,
            ImmutableList.of(
                getMinimalLookBehavior()
            )
        );
    }

    private static void addPlayTasks(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.PLAY,
            ImmutableList.of(
                getFullLookBehavior(),
                Pair.of(99, UpdateActivityFromSchedule.create())
            )
        );
    }

    private static Pair<Integer, BehaviorControl<LivingEntity>> getMinimalLookBehavior() {
        return Pair.of(
            5,
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(SetEntityLookTarget.create(EntityRegistry.REGISTRY.NPC.get(), 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                    Pair.of(new DoNothing(30, 60), 8)
                )
            )
        );
    }

    private static Pair<Integer, BehaviorControl<LivingEntity>> getFullLookBehavior() {
        return Pair.of(
            5,
            new RunOne<>(
                ImmutableList.of(
                    Pair.of(SetEntityLookTarget.create(EntityType.CAT, 8.0F), 8),
                    Pair.of(SetEntityLookTarget.create(EntityRegistry.REGISTRY.NPC.get(), 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(EntityType.PLAYER, 8.0F), 2),
                    Pair.of(SetEntityLookTarget.create(MobCategory.CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.WATER_CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.AXOLOTLS, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.WATER_AMBIENT, 8.0F), 1),
                    Pair.of(SetEntityLookTarget.create(MobCategory.MONSTER, 8.0F), 1),
                    Pair.of(new DoNothing(30, 60), 2)
                )
            )
        );
    }
}
