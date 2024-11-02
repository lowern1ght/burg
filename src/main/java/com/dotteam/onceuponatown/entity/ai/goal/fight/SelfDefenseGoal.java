package com.dotteam.onceuponatown.entity.ai.goal.fight;

import com.dotteam.onceuponatown.entity.Npc;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.boss.wither.WitherBoss;

public class SelfDefenseGoal extends MeleeAttackGoal {

    public SelfDefenseGoal(Npc npc, double speedModifier) {
        super(npc, speedModifier, true);
    }

    @Override
    public boolean canUse() {
        LivingEntity target = this.mob.getTarget();
        boolean validTarget = !(target == null) && !(target instanceof WitherBoss);
        return validTarget && isHurtEnough() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return isHurtEnough() && super.canContinueToUse();
    }

    @Override
    public void start() {
        super.start();
        Npc npc = (Npc)this.mob;
        npc.holdInMainHand(npc.getMeleeWeapon());
    }

    @Override
    public void stop() {
        super.stop();
        ((Npc)this.mob).freeMainHand();
    }

    private boolean isHurtEnough() {
        return (this.mob.getHealth() < this.mob.getMaxHealth() / 2);
    }
}
