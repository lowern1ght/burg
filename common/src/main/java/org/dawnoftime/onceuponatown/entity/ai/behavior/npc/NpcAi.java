package org.dawnoftime.onceuponatown.entity.ai.behavior.npc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.*;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.dawnoftime.onceuponatown.building.instance.Build;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.Profession;
import org.dawnoftime.onceuponatown.entity.ai.behavior.Task;
import org.dawnoftime.onceuponatown.entity.ai.behavior.Tasks;
import org.dawnoftime.onceuponatown.entity.ai.behavior.npc.core.FindBed;
import org.dawnoftime.onceuponatown.entity.ai.behavior.npc.core.NpcCalmDown;
import org.dawnoftime.onceuponatown.entity.ai.behavior.npc.core.NpcPanic;
import org.dawnoftime.onceuponatown.entity.ai.behavior.npc.work.Building;
import org.dawnoftime.onceuponatown.entity.ai.behavior.npc.work.Fishing;
import org.dawnoftime.onceuponatown.registry.EntityRegistry;
import org.dawnoftime.onceuponatown.registry.ScheduleRegistry;

import java.util.function.Predicate;

public class NpcAi {
    public static final ImmutableList<MemoryModuleType<?>> MEMORIES = ImmutableList.of(
        MemoryModuleType.HOME, // Sleep position
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
        MemoryModuleType.INTERACTION_TARGET, // Living entity
        MemoryModuleType.BREED_TARGET,
        MemoryModuleType.PATH,
        MemoryModuleType.DOORS_TO_CLOSE,
        MemoryModuleType.NEAREST_BED,
        MemoryModuleType.HURT_BY, // Damage source
        MemoryModuleType.HURT_BY_ENTITY, // Aggressor
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
    public static final ImmutableList<SensorType<? extends Sensor<? super Npc>>> SENSORS = ImmutableList.of(
        SensorType.NEAREST_LIVING_ENTITIES,
        SensorType.NEAREST_PLAYERS,
        SensorType.NEAREST_ITEMS,
        SensorType.NEAREST_BED,
        SensorType.HURT_BY,
        SensorType.VILLAGER_HOSTILES,
        SensorType.VILLAGER_BABIES,
        //SensorType.SECONDARY_POIS,
        SensorType.GOLEM_DETECTED

    );
    private static final Tasks<Npc> tasks = new Tasks<>();

    public static void setupBrain(Brain<Npc> brain, Npc npc) {
        Profession profession = npc.getProfession();
        // Shared
        setupCoreActivity(brain, 0.5F);
        setupIdleActivity(brain, 0.5F);
        setupPanicActivity(brain, 0.5F);
        setupRestActivity(brain, 0.5F);
        setupMeetActivity(brain, 0.5F);
        setupPreRaidActivity(brain, profession, 0.5F);
        setupRaidActivity(brain, profession, 0.5F);
        setupHideActivity(brain, profession, 0.5F);
        // Babies and work
        if (npc.isBaby()) {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_BABY.get());
            setupPlayActivity(brain, 0.5F);
        } else if (profession != null && profession != Profession.UNEMPLOYED) {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_WORKER.get());
            setupWorkActivity(brain, npc.getProfession(), 0.5F);
        } else {
            brain.setSchedule(ScheduleRegistry.REGISTRY.DEFAULT_UNEMPLOYED.get());
        }
        // Brain initial state
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.updateActivityFromSchedule(npc.level().getDayTime(), npc.level().getGameTime());
    }

    private static void setupCoreActivity(Brain<Npc> brain, float speedModifier) {
        Predicate<Npc> isSwimming = npc -> npc.isInWater() && npc.getFluidHeight(FluidTags.WATER) > npc.getFluidJumpThreshold() || npc.isInLava();
        brain.addActivity(Activity.CORE, ImmutableList.of(
            tasks.ticking("Swim")
                .startIf(isSwimming)
                .stopIf(isSwimming.negate())
                .onTick(npc -> {
                        if (npc.getRandom().nextFloat() < 0.8F) {
                            npc.getJumpControl().jump();
                        }
                    }
                )
                .assemble()
                .priority(0),
            Pair.of(0, InteractWithDoor.create()),
            Pair.of(0, new LookAtTargetSink(45, 20 * 60 * 20)),
            Pair.of(0, new NpcPanic("Panic")),
            Pair.of(0, WakeUp.create()),
            Pair.of(1, new MoveToTargetSink())
            //Pair.of(3, new TradeWithPlayer(speedModifier)),
            //Pair.of(5, GoToWantedItem.create(speedModifier, false, 4)) //
        ));
    }

    private static void setupIdleActivity(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.IDLE, ImmutableList.of(
            tasks.oneRandom("Random behavior")
                .option(2, InteractWith.of(EntityRegistry.REGISTRY.NPC.get(), 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2))
                .option(1, InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2))
                .option(1, RandomStroll.stroll(speedModifier))
                .option(1, SetWalkTargetFromLookTarget.create(speedModifier, 2))
                .option(1, new DoNothing(30, 60))
                .assemble()
                .priority(2),
            Pair.of(3, SetLookAndInteract.create(EntityType.PLAYER, 4)),
            getFullLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static void setupPanicActivity(Brain<Npc> brain, float speedModifier) {
        float f = speedModifier * 1.5F;
        brain.addActivity(Activity.PANIC, ImmutableList.of(
            Pair.of(0, NpcCalmDown.create()),
            Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_HOSTILE, f, 6, false)),
            Pair.of(1, SetWalkTargetAwayFrom.entity(MemoryModuleType.HURT_BY_ENTITY, f, 6, false)),
            Pair.of(3, RandomStroll.stroll(f)),
            getMinimalLookBehavior()
        ));
    }

    private static void setupRestActivity(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.REST, ImmutableList.of(
            Pair.of(3, new SleepInBed()),
            tasks.oneRandom("Random behavior")
                .option(1, FindBed.create(speedModifier))
                .option(4, InsideBrownianWalk.create(speedModifier))
                .option(2, new DoNothing(20, 40))
                .requiresMemories(ImmutableMap.of(MemoryModuleType.HOME, MemoryStatus.VALUE_ABSENT))
                .assemble()
                .priority(5),
            getMinimalLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static void setupWorkActivity(Brain<Npc> brain, Profession profession, float speedModifier) {
        Task<Npc> workBehavior;
        if (profession == Profession.BUILDER) {
            workBehavior = Building.create(speedModifier);
        } else {
            workBehavior = tasks.oneRandom("Random behavior")
                .option(1, InteractWith.of(EntityType.CAT, 8, MemoryModuleType.INTERACTION_TARGET, speedModifier, 2))
                .option(1, RandomStroll.stroll(speedModifier))
                .option(1, SetWalkTargetFromLookTarget.create(speedModifier, 2))
                .option(1, new DoNothing(30, 60))
                .assemble();
        }

        brain.addActivity(Activity.WORK, ImmutableList.of(
            workBehavior.priority(0),
            //getMinimalLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static void setupFightActivity(Brain<Npc> brain, float speedModifier) {
        // TODO set aggressive
        brain.addActivity(Activity.FIGHT, ImmutableList.of());
    }

    private static void setupMeetActivity(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.MEET, ImmutableList.of(
            getFullLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static void setupPreRaidActivity(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.PRE_RAID, ImmutableList.of(
            getMinimalLookBehavior()
        ));
    }

    private static void setupRaidActivity(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.RAID, ImmutableList.of(
            getMinimalLookBehavior()
        ));
    }

    private static void setupHideActivity(Brain<Npc> brain, Profession profession, float speedModifier) {
        brain.addActivity(Activity.HIDE, ImmutableList.of(
            getMinimalLookBehavior()
        ));
    }

    private static void setupPlayActivity(Brain<Npc> brain, float speedModifier) {
        brain.addActivity(Activity.PLAY, ImmutableList.of(
            getFullLookBehavior(),
            Pair.of(99, UpdateActivityFromSchedule.create())
        ));
    }

    private static Pair<Integer, ? extends BehaviorControl<? super Npc>> getMinimalLookBehavior() {
        return tasks.oneRandom("Minimal look")
            .option(2, SetEntityLookTarget.create(EntityRegistry.REGISTRY.NPC.get(), 8.0F))
            .option(2, SetEntityLookTarget.create(EntityType.PLAYER, 8.0F))
            .option(8, new DoNothing(30, 60))
            .assemble()
            .priority(5);
    }

    private static Pair<Integer, ? extends BehaviorControl<? super Npc>> getFullLookBehavior() {
        return tasks.oneRandom("Full look")
            .option(8, SetEntityLookTarget.create(EntityType.CAT, 8.0F))
            .option(2, SetEntityLookTarget.create(EntityRegistry.REGISTRY.NPC.get(), 8.0F))
            .option(2, SetEntityLookTarget.create(EntityType.PLAYER, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.CREATURE, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.WATER_CREATURE, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.AXOLOTLS, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.UNDERGROUND_WATER_CREATURE, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.WATER_AMBIENT, 8.0F))
            .option(1, SetEntityLookTarget.create(MobCategory.MONSTER, 8.0F))
            .option(2, new DoNothing(30, 60))
            .assemble()
            .priority(5);
    }
}
