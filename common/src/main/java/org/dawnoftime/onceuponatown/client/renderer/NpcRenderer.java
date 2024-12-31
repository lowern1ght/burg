package org.dawnoftime.onceuponatown.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.model.layer.NpcArrowLayer;
import org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer;
import org.dawnoftime.onceuponatown.entity.Npc;

public class NpcRenderer extends HumanoidMobRenderer<Npc, NpcModel<Npc>> {
    private static final ResourceLocation NPC_BASE_SKIN = Ouat.modResource("textures/entity/npc/default_skin.png");

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
        //TODO return the culture specific base skin
        return NPC_BASE_SKIN;
    }
}
