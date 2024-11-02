package com.dotteam.onceuponatown.client.renderer;

import com.dotteam.onceuponatown.client.model.NpcModel;
import com.dotteam.onceuponatown.client.model.layer.NpcArrowLayer;
import com.dotteam.onceuponatown.client.model.layer.NpcClothesLayer;
import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.util.OuatUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class NpcRenderer extends HumanoidMobRenderer<Npc, NpcModel<Npc>> {
    private static final ResourceLocation NPC_BASE_SKIN = OuatUtils.resource("textures/entity/npc/base_skin.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel<>(context.bakeLayer(NpcModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new NpcClothesLayer<>(this));
        // TODO : make an armor that fit well the npc body, especially the head
        this.addLayer(new HumanoidArmorLayer<>(this, new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)), new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        //this.addLayer(new CrossedArmsItemLayer<>(this, pContext.getItemInHandRenderer()));
        this.addLayer(new NpcArrowLayer<>( context, this));
    }

    public void render(Npc npc, float entityYaw, float partialTicks, PoseStack matrixStack, MultiBufferSource buffer, int packedLight) {
        super.render(npc, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    protected void scale(Npc npc, PoseStack matrixStack, float partialTickTime) {
        float f = 0.9375F;
        if (npc.isBaby()) {
            f *= 0.5F;
            this.shadowRadius = 0.25F;
        } else {
            this.shadowRadius = 0.5F;
        }
        matrixStack.scale(f, f, f);
    }

    public ResourceLocation getTextureLocation(Npc npc) {
        return NPC_BASE_SKIN;
    }
}
