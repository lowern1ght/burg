package com.dotteam.onceuponatown.client.model.layer;

import com.dotteam.onceuponatown.client.model.NpcModel;
import com.dotteam.onceuponatown.client.renderer.NpcRenderer;
import com.dotteam.onceuponatown.entity.Npc;
import com.dotteam.onceuponatown.util.OuatUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;


/**
 * Draws npcs culture clothes and profession clothes
 */
public class NpcClothesLayer<T extends Npc, M extends NpcModel<T>> extends RenderLayer<T, M> {
    public NpcClothesLayer(NpcRenderer renderer) {
        super((RenderLayerParent<T, M>) renderer);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!npc.isInvisible()) {
            M model = getParentModel();
            renderCultureClothes(model, poseStack, buffer, packedLight, npc);
            renderProfessionClothes(model, poseStack, buffer, packedLight, npc);
        }
    }

    private void renderCultureClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
        //NpcCulture culture = npc.getCulture();
        String path = "textures/entity/npc/culture_clothes/savanna.png";
        ResourceLocation resourceLocation = OuatUtils.resource(path);
        renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, npc, 1.0F, 1.0F, 1.0F);
    }

    private void renderProfessionClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
        //NpcProfession profession = npc.getProfession();
        String path = "textures/entity/npc/profession_clothes/" + "nitwit" + ".png";
        ResourceLocation resourceLocation = OuatUtils.resource(path);
        renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, npc, 1.0F, 1.0F, 1.0F);
    }

    private void renderExperienceBadge(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
    }
}