package org.dawnoftime.onceuponatown.entity.ai.task.work;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.NpcFishingHook;
import org.dawnoftime.onceuponatown.entity.ai.task.GoToPosition;
import org.dawnoftime.onceuponatown.entity.ai.task.SetMemory;
import org.dawnoftime.onceuponatown.entity.ai.task.base.*;

import java.util.concurrent.atomic.AtomicReference;

public class Fishing {
    private static BlockPos findWater(Npc npc) {
        for (BlockPos blockpos : BlockPos.randomInCube(npc.getRandom(), 10, npc.blockPosition(), 8)) {
            if (npc.level().getFluidState(blockpos).is(FluidTags.WATER) && canSeeWater(npc, blockpos)) {
                return blockpos;
            }
        }
        return null;
    }

    public static boolean canSeeWater(Npc npc, BlockPos waterPos) {
        Vec3 vec3 = new Vec3(npc.getX(), npc.getEyeY(), npc.getZ());
        Vec3 vec31 = new Vec3(waterPos.getX(), waterPos.getY(), waterPos.getZ());
        return npc.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc)).getType() == HitResult.Type.MISS;
    }

    public static Task<Npc> catchFish(AtomicReference<BlockPos> waterPos) {
        AtomicReference<ItemEntity> fish = new AtomicReference<>();

        return Sequence.of(
                new SetMemory<>(MemoryModuleType.LOOK_TARGET, npc ->
                    new BlockPosTracker(waterPos.get() == null ? npc.blockPosition() : waterPos.get().above())),
                new DoNothing(40, 60),
                SingleTask.<Npc>run(npc -> {
                    npc.swing(InteractionHand.MAIN_HAND);
                    npc.playSound(SoundEvents.FISHING_BOBBER_THROW);
                    npc.level().addFreshEntity(new NpcFishingHook(npc, npc.level(), waterPos.get()));
                    //Ouat.info(waterPos.get() == null ? "null" : waterPos.get().toShortString());
                }),
                new Task<Npc>()
                    .onTick(npc -> {
                        NpcFishingHook hook = npc.getFishingHook();
                        if (hook != null && hook.isBiting() && npc.getRandom().nextInt(0, 100) < 10) {
                            fish.set(hook.retrieve());
                        }
                    })
                    .stopIf(npc -> npc.getFishingHook() == null)
                    .onStop(npc -> npc.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET)),
                Sequence.of(
                    SingleTask.run(e -> {
                            e.swing(InteractionHand.MAIN_HAND);
                            e.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE);
                    }),

                        new DoNothing(30, 40),
                        SingleTask.<Npc>run(npc -> {
                            if (fish.get() != null) {
                                npc.setReading(true);
                                npc.holdInMainHand(fish.get().getItem());
                                npc.freeOffHand();
                            }
                        }),
                        new DoNothing(30, 40),
                        SingleTask.<Npc>run(npc -> {
                            Item bucket = Items.WATER_BUCKET;
                            if (fish.get() != null) {
                                npc.setReading(false);
                                npc.holdInMainHand(new ItemStack(Items.FISHING_ROD));
                                if (fish.get().getItem().is(Items.COD)) {
                                    bucket = Items.COD_BUCKET;
                                    npc.playSound(SoundEvents.BUCKET_FILL_FISH);
                                } else if (fish.get().getItem().is(Items.SALMON)) {
                                    bucket = Items.SALMON_BUCKET;
                                    npc.playSound(SoundEvents.BUCKET_FILL_FISH);
                                }
                            }
                            npc.holdInOffHand(bucket.getDefaultInstance());
                        })
                    )
                    .startIf(npc -> fish.get() != null)
            )
            .debug("Perform fishing")
            .startIf(npc -> waterPos.get() != null) // Start if water position is founded
            .onStop(npc -> {
                waterPos.set(null);
                fish.set(null);
            });
    }

    public static Pair<Integer, ? extends BehaviorControl<Npc>> create(int priority, float speedModifier) {
        BlockPos fishingPoint = new BlockPos(419, 66, 39);
        int closeEnoughDist = 2;
        AtomicReference<BlockPos> waterPos = new AtomicReference<>();

        var taskTree = Selector.firstValid(
            new GoToPosition<Npc>(npc -> fishingPoint, closeEnoughDist, 0.5F)
                .debug("Go to fishing point")
                .defaultPriority(),
            Repeater.of(
                    Selector.firstValid(
                            Pair.of(0, SingleTask.<Npc>run(npc -> waterPos.set(findWater(npc)))
                                .startIf(npc -> waterPos.get() == null)
                            ),
                            Pair.of(0, catchFish(waterPos))
                        )
                        .debug("Find water or use fishing rod")
                )
                .debug("Fish at fishing point")
                .restrictTo(npc -> fishingPoint, closeEnoughDist) // End fishing if too far away
                .onStart(npc -> npc.holdInHands(new ItemStack(Items.FISHING_ROD), new ItemStack(Items.WATER_BUCKET)))
                .onStop(Npc::freeHands)
                .defaultPriority()

        )
        .debug("Fishing logic")
        .forActivity(Activity.WORK);

        return Pair.of(priority, taskTree);
    }
}
