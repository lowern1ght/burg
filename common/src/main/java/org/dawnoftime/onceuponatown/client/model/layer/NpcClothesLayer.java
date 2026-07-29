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
 *
 * <p><b>This layer survived the move to hand-drawn bodies, and it is the reason those bodies are
 * UNDERCLOTHES.</b> One tunic file per trade over any body is what makes a farmer distinguishable
 * from a smith, and it multiplies: 7 garments x N bodies x the head outlines. Draw finished
 * characters instead — a knight in mail, a lady in a green gown — and a tunic over them is a mess,
 * this layer has to go, and the drawn pool then has to cover 7 professions x 2 sexes itself before
 * there is any variety inside a trade. So the bodies are a shift and hose and this layer stays.
 *
 * <p>There is therefore <b>no drawn-character-versus-garment conflict to opt out of</b>. The
 * coupling is a gate instead: {@code tools/draw_citizens.py} reads the mask off the shipped
 * garment files and checks that what the sleeveless V leaves open is the shift or the skin under
 * it, never something that belongs further down the body.
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
        int wealth = NpcLook.wealthOf(npc);
        // The last argument is an ARGB COLOUR and the overlay coords are computed inside
        // renderColoredCutoutModel — read out of the 1.21.1 bytecode, because the parameter is
        // a bare int and the 1.20.1 signature this was ported from ended in three colour
        // floats instead. Passing the overlay here handed it 0x000A0000, whose alpha byte is
        // zero: every garment in the mod was drawn fully transparent.
        renderColoredCutoutModel(getParentModel(), clothes, poseStack, buffer,
            packedLight, npc, NpcLook.clothesTint(npc, wealth));

        // THE BRAID, at the top of the wealth ladder only. A second pass over the same geometry
        // with a second texture and its OWN tint, which is the only way a gold edge can sit on a
        // woad gown — one tint over both would give a lighter edge of the same colour.
        //
        // Not a separate RenderLayer, and not a z-fight either. Vanilla's own
        // `VillagerProfessionLayer` draws this same geometry up to three times over (biome type,
        // profession, level) and it resolves because Minecraft's depth function is LEQUAL, so a
        // later co-planar pass wins. Doing it here rather than in a fourth layer class also keeps
        // the two passes in one place and leaves the three renderers untouched.
        ResourceLocation trim = NpcLook.trim(npc, wealth);
        if (trim != null) {
            renderColoredCutoutModel(getParentModel(), trim, poseStack, buffer,
                packedLight, npc, NpcLook.trimTint(npc, wealth));
        }
    }
}
