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
import org.dawnoftime.onceuponatown.client.CitizenLook;
import org.dawnoftime.onceuponatown.client.model.NpcModel;
import org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer;
import org.dawnoftime.onceuponatown.client.model.layer.NpcHairLayer;
import org.dawnoftime.onceuponatown.entity.Npc;

public class NpcRenderer extends HumanoidMobRenderer<Npc, NpcModel<Npc>> {

    /**
     * The builder's body, chosen rather than rolled — and it had to be chosen because of an
     * accident worth recording.
     *
     * <p>{@code citizen_skin_0.png} and {@code default_skin.png} were <b>the same file</b>: git
     * blob {@code 4f5ef400} for both, because the old pipeline's first complexion was the identity
     * transform. So the most-seen NPC in the game was silently on the citizen path and wore that
     * set's worst artefact — a torso of thirteen desaturated greys at median luminance 98, which
     * is the real reason he read as wearing a black cloak. It was never a bug in the garment code.
     *
     * <p>He is now the {@code warm} complexion with the {@code lined} face, and
     * {@link CitizenLook#BUILDER} gives him a short beard to match. {@code default_skin.png} stays
     * on disk untouched; nothing reads it any more.
     */
    private static final ResourceLocation NPC_BASE_SKIN = CitizenLook.body(CitizenLook.BUILDER);

    /** Kept so the file it names is not orphaned silently. Nothing reads it. */
    private static final ResourceLocation RETIRED_DEFAULT_SKIN =
        ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "textures/entity/npc/default_skin.png");

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel<>(context.bakeLayer(NpcModel.LAYER_LOCATION)), 0.5F);
        this.addLayer(new NpcClothesLayer<>(this));
        // Hair, beard and headwear: PAINT on the `hat` cube the rig already carries, not
        // geometry. 31 of 31 reference skins use the head's second layer; the cubes this
        // replaces also cost a black screen. See NpcHairLayer.
        this.addLayer(new NpcHairLayer<>(this));
        this.addLayer(new HumanoidArmorLayer<>(this,
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
            new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
            context.getModelManager()));
    }

    @Override
    public void render(Npc npc, float entityYaw, float partialTicks, PoseStack matrixStack,
                        MultiBufferSource buffer, int packedLight) {
        super.render(npc, entityYaw, partialTicks, matrixStack, buffer, packedLight);
    }

    @Override
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

    @Override
    public ResourceLocation getTextureLocation(Npc npc) {
        return NPC_BASE_SKIN;
    }

    /** Shown when the player looks at him — see TownVillagerRenderer for the reasoning. */
    @Override
    protected boolean shouldShowName(Npc npc) {
        if (npc.isCustomNameVisible()) return true;
        return npc == this.entityRenderDispatcher.crosshairPickEntity;
    }

    /** A name tag wins; the builder's own name survives underneath it. */
    @Override
    protected void renderNameTag(Npc npc, net.minecraft.network.chat.Component displayName,
                                 PoseStack pose, MultiBufferSource buffer,
                                 int packedLight, float partialTick) {
        net.minecraft.network.chat.Component label = npc.hasCustomName()
            ? displayName
            : net.minecraft.network.chat.Component.literal(npc.givenName());
        super.renderNameTag(npc, label, pose, buffer, packedLight, partialTick);
    }
}
