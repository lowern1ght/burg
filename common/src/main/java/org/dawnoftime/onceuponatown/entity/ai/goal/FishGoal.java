package org.dawnoftime.onceuponatown.entity.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.NpcFishingHook;

import java.util.EnumSet;

public class FishGoal extends NpcGoal {
    private BlockPos waterPos;
    private boolean hookThrown;

    public FishGoal(Npc npc) {
        super(npc);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return super.canUse() && lookForWater() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public void start() {
        super.start();
        npc.holdInMainHand(new ItemStack(Items.FISHING_ROD));
        npc.getLookControl().setLookAt(waterPos.getCenter());
        level().playSound(null, npc.getX(), npc.getY(), npc.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.NEUTRAL, 0.5F, 0.4F / (level().getRandom().nextFloat() * 0.4F + 0.8F));

    }

    @Override
    public void tick() {
        super.tick();
        if (npc.getLookControl().isLookingAtTarget() && !hookThrown && npcPos().closerThan(waterPos, 10.0D)) {
            level().addFreshEntity(new NpcFishingHook(npc, level()));
            hookThrown = true;
        }
    }

    protected BlockPos lookForWater() {
        if (!level().getBlockState(npcPos()).getCollisionShape(level(), npcPos()).isEmpty()) {
            return null;
        } else {
            waterPos = BlockPos.findClosestMatch(npcPos(), 20, 10, (posCandidate) -> level().getFluidState(posCandidate).is(FluidTags.WATER)).orElse(null);
            return waterPos;
        }
    }

}
