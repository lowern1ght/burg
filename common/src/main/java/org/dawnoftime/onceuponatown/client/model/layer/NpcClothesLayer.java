package org.dawnoftime.onceuponatown.client.model.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.dawnoftime.onceuponatown.client.NpcLook;
import org.dawnoftime.onceuponatown.client.model.NpcModel;

/**
 * Draws the entity's clothing over its skin, re-rendering the same mesh with a second
 * texture. Which texture and which shade are the ENTITY's business, not the layer's: it once
 * named one builder outfit as a constant, so every town NPC sharing this rig would have worn
 * the builder's clothes whatever job it held.
 */
public class NpcClothesLayer<T extends Mob, M extends NpcModel<T>>
        extends RenderLayer<T, M> {

    public NpcClothesLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc,
                        float limbSwing, float limbSwingAmount, float partialTick,
                        float ageInTicks, float netHeadYaw, float headPitch) {
        ResourceLocation clothes = NpcLook.clothes(npc);
        if (clothes == null || npc.isInvisible()) return;
        // The last argument is an ARGB COLOUR and the overlay coords are computed inside
        // renderColoredCutoutModel — read out of the 1.21.1 bytecode, because the parameter is
        // a bare int and the 1.20.1 signature this was ported from ended in three colour
        // floats instead. Passing the overlay here handed it 0x000A0000, whose alpha byte is
        // zero: every garment in the mod was drawn fully transparent.
        renderColoredCutoutModel(getParentModel(), clothes, poseStack, buffer,
            packedLight, npc, NpcLook.clothesTint(npc));
    }
}
