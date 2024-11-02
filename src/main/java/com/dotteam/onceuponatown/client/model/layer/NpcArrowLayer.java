package com.dotteam.onceuponatown.client.model.layer;

import com.dotteam.onceuponatown.client.model.NpcModel;
import com.dotteam.onceuponatown.entity.Npc;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Arrow;

public class NpcArrowLayer<T extends Npc, M extends NpcModel<T>> extends NpcStuckInBodyLayer<T, M> {
    private final EntityRenderDispatcher dispatcher;

    public NpcArrowLayer(EntityRendererProvider.Context context, LivingEntityRenderer<T, M> renderer) {
        super(renderer);
        this.dispatcher = context.getEntityRenderDispatcher();
    }

    protected int numStuck(T pEntity) {
        return pEntity.getArrowCount();
    }

    protected void renderStuckItem(PoseStack pPoseStack, MultiBufferSource pBuffer, int pPackedLight, Entity pEntity, float pX, float pY, float pZ, float pPartialTick) {
        float f = Mth.sqrt(pX * pX + pZ * pZ);
        Arrow arrow = new Arrow(pEntity.level(), pEntity.getX(), pEntity.getY(), pEntity.getZ());
        arrow.setYRot((float)(Math.atan2((double)pX, (double)pZ) * (double)(180F / (float)Math.PI)));
        arrow.setXRot((float)(Math.atan2((double)pY, (double)f) * (double)(180F / (float)Math.PI)));
        arrow.yRotO = arrow.getYRot();
        arrow.xRotO = arrow.getXRot();
        this.dispatcher.render(arrow, 0.0D, 0.0D, 0.0D, 0.0F, pPartialTick, pPoseStack, pBuffer, pPackedLight);
    }
}
