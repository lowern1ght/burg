package org.dawnoftime.onceuponatown.entity.ai.work;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
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
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Task;
import org.dawnoftime.onceuponatown.entity.ai.controlflow.Tasks;
import org.dawnoftime.onceuponatown.entity.ai.core.GoToPosition;
import org.dawnoftime.onceuponatown.entity.ai.core.SetMemory;

import java.util.concurrent.atomic.AtomicReference;
import static net.minecraft.world.entity.ai.memory.MemoryModuleType.*;

public class Fishing {
    public static Task<Npc> create() {
        BlockPos fishingPoint = new BlockPos(-986, 64, -383);
        int closeEnoughDist = 2;
        AtomicReference<BlockPos> waterPos = new AtomicReference<>();
        AtomicReference<ItemEntity> fish = new AtomicReference<>();

        Tasks<Npc> tasks = new Tasks<>();

        var performFishing = tasks.sequence("Perform fishing");

        performFishing.step(new SetMemory<>("Look at throw target", LOOK_TARGET,
            npc -> new BlockPosTracker(waterPos.get() == null ? npc.blockPosition() : waterPos.get().above()))
        );

        performFishing.step(new DoNothing(40, 60));

        performFishing.step(tasks.single("Throw bobber", npc -> {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.playSound(SoundEvents.FISHING_BOBBER_THROW);
            npc.level().addFreshEntity(new NpcFishingHook(npc, npc.level(), waterPos.get()));}).assemble()
        );

        performFishing.step(tasks.ticking("Try retrieve bobber")
            .onTick(npc -> {
                NpcFishingHook hook = npc.getFishingHook();
                if (hook != null && hook.isBiting() && npc.getRandom().nextInt(0, 100) < 10) {
                    fish.set(hook.retrieve());
                }
            })
            .stopIf(npc -> npc.getFishingHook() == null)
            .onStop(npc -> npc.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET))
            .assemble()
        );

        var succeedRetrieve = tasks.sequence("Succeed retrieve bobber");

        succeedRetrieve.step(tasks.single("Retrieve bobber", npc -> {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE);}).assemble()
        );

        // Wait for fish in hands
        succeedRetrieve.step(new DoNothing(30, 40));

        succeedRetrieve.step(tasks.single("Admire fish", npc -> {
            if (fish.get() != null) {
                npc.setReading(true);
                npc.holdInMainHand(fish.get().getItem());
                npc.freeOffHand();
            }}).assemble()
        );

        // Admire fish for a while
        succeedRetrieve.step(new DoNothing(30, 40));

        succeedRetrieve.step(tasks.single("Put fish in bucket", npc -> {
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
            npc.holdInOffHand(bucket.getDefaultInstance());}).assemble()
        );

        succeedRetrieve.startIf(npc -> fish.get() != null);

        performFishing.step(succeedRetrieve.assemble());

        // Start if water position is founded
        performFishing.startIf(npc -> waterPos.get() != null);

        performFishing.onStop(npc -> {
            waterPos.set(null);
            fish.set(null);
        });

        var fishAtPoint = tasks.firstValid("Fish at point");
        fishAtPoint.option(1, tasks.single("Choose where to throw", npc -> waterPos.set(findWater(npc)))
            .startIf(npc -> waterPos.get() == null)
            .assemble()
        );
        fishAtPoint.option(1, performFishing.assemble());

        var fishAtPointOrGetCloser = tasks.firstValid("Fish at point or get closer");
        fishAtPointOrGetCloser.option(1, new GoToPosition<>("Get closer to fishing position",
            npc -> fishingPoint, closeEnoughDist, 1.0F));
        fishAtPointOrGetCloser.option(1, tasks.repeat("Repeat fishing at point", fishAtPoint.assemble())
            .restrictPosition(npc -> fishingPoint, closeEnoughDist)
            .onStart(npc -> npc.holdInHands(new ItemStack(Items.FISHING_ROD), new ItemStack(Items.WATER_BUCKET)))
            .onStop(Npc::freeHands)
            .assemble()
        );
        fishAtPointOrGetCloser.forActivity(Activity.WORK);

        return fishAtPointOrGetCloser.assemble();
    }

    public static boolean canSeeWater(Npc npc, BlockPos waterPos) {
        Vec3 vec3 = new Vec3(npc.getX(), npc.getEyeY(), npc.getZ());
        Vec3 vec31 = new Vec3(waterPos.getX(), waterPos.getY(), waterPos.getZ());
        return npc.level().clip(new ClipContext(vec3, vec31, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc)).getType() == HitResult.Type.MISS;
    }

    private static BlockPos findWater(Npc npc) {
        for (BlockPos blockpos : BlockPos.randomInCube(npc.getRandom(), 10, npc.blockPosition(), 8)) {
            if (npc.level().getFluidState(blockpos).is(FluidTags.WATER) && canSeeWater(npc, blockpos)) {
                return blockpos;
            }
        }
        return null;
    }
}
