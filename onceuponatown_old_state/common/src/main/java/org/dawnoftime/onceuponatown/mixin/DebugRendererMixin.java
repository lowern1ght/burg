package org.dawnoftime.onceuponatown.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.dawnoftime.onceuponatown.Config;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DebugRenderer.class)
public class DebugRendererMixin {
    @Inject(method = "render", at = @At(value = "RETURN"))
    public void enablePathfindingRenderer(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, double camX, double camY, double camZ, CallbackInfo ci) {
        if (Config.DEBUG_PATHFINDING) {
            Minecraft.getInstance().debugRenderer.pathfindingRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
        if (Config.DEBUG_GOALS) {
            Minecraft.getInstance().debugRenderer.goalSelectorRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
        if (Config.DEBUG_BRAINS) {
            Minecraft.getInstance().debugRenderer.brainDebugRenderer.render(poseStack, bufferSource, camX, camY, camZ);
        }
    }
}
