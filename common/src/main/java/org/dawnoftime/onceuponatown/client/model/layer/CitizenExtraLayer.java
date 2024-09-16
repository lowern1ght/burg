package org.dawnoftime.onceuponatown.client.model.layer;

import org.dawnoftime.onceuponatown.client.model.CitizenModel;
import org.dawnoftime.onceuponatown.client.renderer.CitizenRenderer;
import org.dawnoftime.onceuponatown.entity.Citizen;
import org.dawnoftime.onceuponatown.entity.CitizenCulture;
import org.dawnoftime.onceuponatown.entity.CitizenProfession;
import org.dawnoftime.onceuponatown.util.OuatUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;


/**
 * Draws citizens culture clothes and profession clothes
 */
public class CitizenExtraLayer<T extends Citizen, M extends CitizenModel<T>> extends RenderLayer<T, M> {
    public CitizenExtraLayer(CitizenRenderer renderer) {
        super((RenderLayerParent<T, M>) renderer);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T citizen, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!citizen.isInvisible()) {
            M model = getParentModel();
            renderCultureClothes(model, poseStack, buffer, packedLight, citizen);
            renderProfessionClothes(model, poseStack, buffer, packedLight, citizen);
        }
    }

    private void renderCultureClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T citizen) {
        CitizenCulture culture = citizen.getCulture();
        String path = "textures/entity/citizen/culture_clothes/" + "savanna" + ".png";
        ResourceLocation resourceLocation = OuatUtils.resource(path);
        renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, citizen, 1.0F, 1.0F, 1.0F);
    }

    private void renderProfessionClothes(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T citizen) {
        CitizenProfession profession = citizen.getProfession();
        if (profession != CitizenProfession.UNEMPLOYED) {
            String path = "textures/entity/citizen/profession_clothes/" + "farmer" + ".png";
            ResourceLocation resourceLocation = OuatUtils.resource(path);
            renderColoredCutoutModel(model, resourceLocation, poseStack, buffer, packedLight, citizen, 1.0F, 1.0F, 1.0F);
        }
    }

    private void renderXpLevel(M model, PoseStack poseStack, MultiBufferSource buffer, int packedLight, T citizen) {
    }
}