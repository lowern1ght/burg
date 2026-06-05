package org.dawnoftime.onceuponatown.mixin;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Pillager.class)
public abstract class PillagerMixin extends Mob {
    protected PillagerMixin(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "registerGoals", at = @At(value = "TAIL"))
    protected void registerGoals(CallbackInfo ci) {
        //this.targetSelector.addGoal(3, new NearestAttackableTargetGoal((Pillager)(Object)this, Npc.class, false));
    }
}
