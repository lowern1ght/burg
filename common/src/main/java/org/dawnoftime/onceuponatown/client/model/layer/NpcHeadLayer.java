package org.dawnoftime.onceuponatown.client.model.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.client.CitizenLook;
import org.dawnoftime.onceuponatown.client.model.NpcHeadModels;
import org.dawnoftime.onceuponatown.client.model.NpcModel;

/**
 * Hair, beard and headwear, drawn on the head.
 *
 * <p>Three cubes' worth of geometry rather than paint, because a texture cannot change a
 * silhouette on this rig — the twelve skins the mod shipped had three distinct alpha masks
 * between them and the six men shared one. See {@link CitizenLook} for the full reasoning.
 *
 * <p>The hair and the beard take the SAME colour from the same roll and share one material. That
 * is not tidiness: {@code LivingEntityRenderer.render} passes a hardcoded {@code -1} as the base
 * model colour, so a beard painted into the body texture could never have followed the hair's
 * tint, and a grey-haired man would have had a brown beard. Headwear is cloth and takes its own
 * colour per kind.
 */
public class NpcHeadLayer<T extends Mob, M extends NpcModel<T>> extends RenderLayer<T, M> {

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/entity/npc/" + name + ".png");
    }

    /** Uniform, near-white, fully opaque. Every cube samples texOffs(0,0) of it. */
    private static final ResourceLocation HAIR_MATERIAL = tex("npc_hair");
    private static final ResourceLocation CLOTH_MATERIAL = tex("npc_headwear");

    /**
     * The colour of each headwear kind, indexed as {@code NpcHeadModels.HEADWEAR_LAYERS}.
     *
     * <p>Per kind rather than per person, because these are materials and not dyes: linen is
     * bleached or it is not, straw is straw. They multiply the near-white twill, the same
     * arrangement {@code NpcLook.TINTS} uses, and they stay close to white for the reason that
     * table gives — a strong shift stops reading as cloth and starts reading as a team colour.
     * Index 0 is bare and is never drawn.
     */
    private static final int[] HEADWEAR_COLOUR = {
        0xFFFFFFFF,   // 0 bare, unused
        0xFFE8E2D2,   // 1 coif — unbleached linen
        0xFFD8BE7E,   // 2 straw hat
        0xFF9A9084,   // 3 hood — undyed grey-brown wool
        0xFFB0A498,   // 4 cap — the same wool, lighter
        0xFFEFEADA,   // 5 veil — bleached linen, the one thing a household bleached
        0xFFE2DDC9,   // 6 wimple
    };

    private final ModelPart[] hair;
    private final ModelPart[] beards;
    private final ModelPart[] headwear;

    public NpcHeadLayer(RenderLayerParent<T, M> renderer, EntityRendererProvider.Context context) {
        super(renderer);
        this.hair = bake(context, NpcHeadModels.HAIR_LAYERS);
        this.beards = bake(context, NpcHeadModels.BEARD_LAYERS);
        this.headwear = bake(context, NpcHeadModels.HEADWEAR_LAYERS);
    }

    /**
     * Bake every variant once, at construction.
     *
     * <p>A null slot is a variant that has no model — beard 0 and headwear 0, which are the
     * absence of the thing. {@code bakeLayer} would throw on an unregistered location, so the
     * registration in {@code OuatForgeClient} and the null slots here have to agree; both read
     * the same arrays out of {@link NpcHeadModels}.
     */
    private ModelPart[] bake(EntityRendererProvider.Context context,
                             net.minecraft.client.model.geom.ModelLayerLocation[] locations) {
        ModelPart[] out = new ModelPart[locations.length];
        for (int i = 0; i < locations.length; i++) {
            out[i] = context.getModelSet().bakeLayer(locations[i]).getChild("head");
        }
        return out;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight, T npc,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        if (npc.isInvisible()) return;
        CitizenLook.Look look = CitizenLook.of(npc);

        pose.pushPose();
        /*
         * THE BABY BRANCH IS NOT A PRECAUTION. `Npc` extends `AgeableMob` and its
         * `getBreedOffspring` returns one, so children exist and are reachable.
         *
         * `getHead().translateAndRotate` gives the head PART's own transform and nothing else,
         * but `AgeableListModel.renderToBuffer` wraps the head parts in a group transform when
         * `young`, and `HumanoidModel`'s constructor supplies the numbers:
         *
         *     super(renderType, scaleHead = true, babyYHeadOffset = 16.0F, babyZHeadOffset = 0.0F,
         *           babyHeadScale = 2.0F, babyBodyScale = 2.0F, bodyYOffset = 24.0F);
         *
         * which makes the group `scale(1.5 / 2.0)` then `translate(0, 16 / 16, 0)`. Reproduced
         * here, or every child's hair floats at adult height and adult size. Vanilla's
         * `CustomHeadLayer` carries the same branch and excludes villagers from it only because
         * `VillagerModel` differs; ours is humanoid, so it needs the humanoid numbers.
         */
        if (npc.isBaby()) {
            pose.scale(0.75F, 0.75F, 0.75F);
            pose.translate(0.0F, 1.0F, 0.0F);
        }
        // Follows the head in all three axes, including the reading pose's pitch and the zRot
        // that `NpcModel.setupAnim` zeroes. `HumanoidModel` implements `HeadedModel`, so this is
        // the same call vanilla's own head layer makes.
        getParentModel().getHead().translateAndRotate(pose);

        VertexConsumer strands = buffer.getBuffer(RenderType.entityCutoutNoCull(HAIR_MATERIAL));
        hair[Math.floorMod(look.hairStyle(), hair.length)]
            .render(pose, strands, packedLight, OverlayTexture.NO_OVERLAY, look.hairColour());

        ModelPart beard = beards[Math.floorMod(look.beard(), beards.length)];
        if (beard != null) {
            // The beard is the hair's colour by construction — see the class notes.
            beard.render(pose, strands, packedLight, OverlayTexture.NO_OVERLAY,
                         look.hairColour());
        }

        int hat = Math.floorMod(look.headwear(), headwear.length);
        ModelPart worn = headwear[hat];
        if (worn != null) {
            VertexConsumer cloth =
                buffer.getBuffer(RenderType.entityCutoutNoCull(CLOTH_MATERIAL));
            worn.render(pose, cloth, packedLight, OverlayTexture.NO_OVERLAY,
                        HEADWEAR_COLOUR[hat]);
        }
        pose.popPose();
    }
}
