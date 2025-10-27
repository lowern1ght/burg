package org.dawnoftime.onceuponatown.client.model.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.renderer.NpcRenderer;
import org.dawnoftime.onceuponatown.datapack.core.DataHandler;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.culture.Profession;
import org.jetbrains.annotations.NotNull;


/**
 * Draws npcs culture clothes and profession clothes
 */
public class NpcClothesLayer<T extends Npc, M extends NpcModel<T>> extends RenderLayer<T, M> {
    public NpcClothesLayer(NpcRenderer renderer) {
        super((RenderLayerParent<T, M>) renderer);// TODO I have a warning here, how can we fix it ?
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, T npc, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!npc.isInvisible()) {
            M model = getParentModel();
            renderCultureCommonClothes(model, poseStack, buffer, packedLight, npc);
            renderProfessionClothes(model, poseStack, buffer, packedLight, npc);
        }
    }

    private void renderCultureCommonClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
        String cultureId = npc.getCultureId();
        if (!cultureId.equals("default") && !cultureId.isBlank()) {
            String path = DataHandler.CULTURES_FOLDER_NAME + "/" + cultureId + "/clothes/jacket.png";
            ResourceLocation resourceLocation = Ouat.modResource(path);
            renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, npc, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderProfessionClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
        String cultureId = npc.getCultureId();
        String professionId = npc.getProfessionId();
        if (!cultureId.equals("default") && !cultureId.isBlank()) {
            if (!professionId.isBlank() && !professionId.equals("default") && !professionId.equals(Profession.UNEMPLOYED.getId())) {
                String path = DataHandler.CULTURES_FOLDER_NAME + "/" + cultureId + "/clothes/professions/" + professionId + ".png";
                ResourceLocation resourceLocation = Ouat.modResource(path);
                renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, npc, 1.0F, 1.0F, 1.0F);
            }
        }
    }

    private void renderExperienceBadge(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc) {
    }
}