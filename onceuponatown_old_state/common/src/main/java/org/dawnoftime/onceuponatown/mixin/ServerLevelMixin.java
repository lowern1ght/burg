package org.dawnoftime.onceuponatown.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
public class ServerLevelMixin {
    @Inject(method = "isVillage(Lnet/minecraft/core/BlockPos;)Z", at = @At(value = "HEAD"), cancellable = true)
    public void isVillage(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        /*
        if (LevelTowns.of((ServerLevel)(Object)this).getTownAt(pos) != null) {
            cir.setReturnValue(true);
        }
        */
    }
}
