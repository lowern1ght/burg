package org.lowern1ght.burg.client.model.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.lowern1ght.burg.client.CitizenLook;
import org.lowern1ght.burg.client.model.NpcModel;

/**
 * Hair, beard and headwear — <b>painted</b>, on the head's own second-layer cube.
 *
 * <h2>What this replaces, and the measurement that overturned it</h2>
 *
 * <p>The pass before this one built {@code NpcHeadModels}: 42 baked cubes for five hair styles,
 * three beards and six coverings, on the argument that <i>a texture cannot carry a silhouette on
 * this rig, so the outline has to be the model</i>. That argument is wrong, and the number that
 * refutes it was already in hand and already quoted in the brief that commissioned the geometry:
 * of the owner's <b>31 reference skins, 31 use the head's second layer</b>. That is how every long
 * hair, hat, hood and coif in Minecraft is drawn, for players and villagers alike — an inflated
 * shell whose texture's <b>alpha carves the outline</b>. Vanilla ships no hair models at all.
 *
 * <p>It also cost a black screen: {@code No model for layer burg:npc_beard#v0}, because
 * the registration loop baked every location in an array whose absent variants were nulls.
 * Paint cannot fail that way — a missing texture is a missing texture, not a crash.
 *
 * <p>Measured on the same 31 files, so the shells are the right size:
 *
 * <pre>
 *   head second layer painted                      31 of 31
 *   hat coverage, of the cube's 384 net texels      median 73  (min 10, max 316)
 *   reaches the CHIN course (row 7 of a side face)  23 of 31
 * </pre>
 *
 * <h2>Nothing about the mesh changes</h2>
 *
 * <p>{@code NpcModel.createBodyLayer} already puts {@code hat} at {@code texOffs(32, 0)} as an
 * 8x8x8 cube inflated {@code +0.5}, and {@code setupAnim} already does {@code hat.copyFrom(head)},
 * so it follows the head in yaw, pitch and roll. This layer re-renders the whole model with a
 * texture that is transparent everywhere except that cube's net.
 *
 * <h2>Why three passes over one cube do not z-fight</h2>
 *
 * <p>Hair, then beard, then covering, all on the same shell at the same inflation. That resolves
 * because Minecraft's depth function is {@code LEQUAL}, so a later co-planar pass wins — which is
 * exactly what vanilla's own {@code VillagerProfessionLayer} relies on when it draws the same
 * villager geometry three times over for type, profession and level. A hood therefore covers hair
 * by being drawn after it, not by being further out.
 *
 * <h2>Tint, and why hair colour is a free axis at all</h2>
 *
 * <p>{@code LivingEntityRenderer.render} ends its base pass with a hardcoded {@code -1} for the
 * model colour, so the drawn body cannot be tinted; every {@link RenderLayer} takes an ARGB int.
 * That asymmetry is the whole reason complexion is a drawn file and hair colour is a multiply. The
 * paintings are therefore in near-white greys, the same way {@code npc_hair.png} was and for the
 * same reason: a multiply can only darken, so a texture drawn dark can never be fair.
 *
 * <p><b>The beard shares the hair's texture pass order and its tint</b>, which was the one good
 * argument in the geometry plan and survives unchanged: a beard that could not follow the hair's
 * colour would have put a brown beard on a grey-haired man.
 *
 * <h2>Hair past the jaw — measured, and the answer is "do not"</h2>
 *
 * <p>The obvious worry is that shoulder-length hair needs the torso's second layer, which
 * {@link NpcClothesLayer} already owns for the trade tunic. Measured on the references by matching
 * the shoulder paint against each file's own crown colour: of the 24 that paint both a head shell
 * and the shoulder, <b>2 continue the hair colour onto the torso and 15 put a garment there</b>.
 * So the corpus does not do it either, and no ordering rule is needed. The cube reaches the chin
 * and 23 of 31 references paint down to that course; jaw-length is both the rig's limit and the
 * corpus's habit.
 */
public class NpcHairLayer<T extends Mob, M extends NpcModel<T>> extends RenderLayer<T, M> {

    public NpcHairLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (npc.isInvisible()) return;
        CitizenLook.Look look = CitizenLook.of(npc);

        ResourceLocation hair = CitizenLook.hair(look);
        if (hair != null) {
            renderColoredCutoutModel(getParentModel(), hair, poseStack, buffer,
                packedLight, npc, look.hairColour());
        }
        ResourceLocation beard = CitizenLook.beard(look);
        if (beard != null) {
            // The hair's own colour, deliberately. See the class note.
            renderColoredCutoutModel(getParentModel(), beard, poseStack, buffer,
                packedLight, npc, look.hairColour());
        }
        ResourceLocation covering = CitizenLook.headwear(look);
        if (covering != null) {
            renderColoredCutoutModel(getParentModel(), covering, poseStack, buffer,
                packedLight, npc, CitizenLook.headwearTint(look));
        }
    }
}
