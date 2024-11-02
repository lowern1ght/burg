package com.dotteam.onceuponatown.entity.ai.goal.core;

import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.entity.ai.goal.NpcGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;

public class DirtTowerEscapeGoal extends NpcGoal {
    private int progress;

    public DirtTowerEscapeGoal(Npc npc) {
        super(npc);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        cooldown(UniformInt.of(20 * 8, 20 * 14));
    }

    public boolean canUse() {
        var lastAttacker = npc.getLastAttacker();
        return super.canUse() && isHurtEnough() && lastAttacker != null && npc.distanceTo(lastAttacker) >= 6F;
    }

    public boolean canContinueToUse() {
        return progress < 4;
    }

    public void start() {
        this.progress = 0;
        npc.getNavigation().stop();
        npc.holdInMainHand(new ItemStack(Items.DIRT));
    }

    public void tick() {
        boolean standingStill = npc.getDeltaMovement().x() == 0.0D && this.npc.getDeltaMovement().z() == 0.0D;
        BlockPos bellowPos = npc.blockPosition().below();

        npc.getLookControl().setLookAt(bellowPos.getX(), bellowPos.getY(), bellowPos.getZ());
        if(npc.onGround() && standingStill){
            npc.getJumpControl().jump();
        }
        if(!npc.level().getBlockState(bellowPos).isSolid() && standingStill) {
            npc.swing(InteractionHand.MAIN_HAND);
            npc.level().setBlock(bellowPos, Blocks.DIRT.defaultBlockState(), 2);
            npc.playSound(Blocks.DIRT.defaultBlockState().getSoundType().getPlaceSound());
            ++progress;
        }
    }

    private boolean isHurtEnough() {
        return (npc.getHealth() < npc.getMaxHealth() / 2);
    }
}

