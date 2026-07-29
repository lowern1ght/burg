/*
 * ***** RETIRED, AND KEPT ON DISK ON PURPOSE. NOTHING REFERENCES THIS FILE. *****
 *
 * Replaced by `NpcHairLayer`, which paints hair, beards and headwear on the head's own
 * second-layer cube instead of baking geometry for them.
 *
 * WHY, IN ONE NUMBER. The whole argument below — "a texture cannot change an outline, so the
 * silhouette has to be the model" — is contradicted by a measurement that already existed when
 * this file was written: of the owner's 31 reference skins, **31 use the head's second layer**.
 * That is how every long hair, hat, hood and coif in Minecraft is drawn, for players and villagers
 * alike: an inflated shell whose texture's ALPHA carves the outline. Vanilla ships no hair models
 * at all. The three-alpha-mask measurement quoted below is real but says something else — it says
 * the twelve skins the mod SHIPPED never used the lever, not that the lever does not exist.
 *
 * IT ALSO COST A BLACK SCREEN: `No model for layer onceuponatown:npc_beard#v0`, fixed in a6ada7f.
 * A registration loop over an array whose absent variants are nulls is a failure mode paint does
 * not have — a missing texture is a missing texture, not a crash.
 *
 * Not deleted, by repo law: never delete before verifying, and ask first. The 42 cube definitions
 * are here if the decision is ever revisited, and `NpcHeadLayer` beside it is the renderer that
 * drove them.
 */
package org.dawnoftime.onceuponatown.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.Ouat;

import java.util.function.Supplier;

/**
 * Hair, beards and headwear, as geometry on the head.
 *
 * <h2>Why these are cubes and not paint</h2>
 *
 * <p>Because a texture cannot change an outline. Across the twelve skins the mod shipped there
 * were <b>three distinct alpha masks</b>: the six men shared one. Whatever is drawn into a skin,
 * every citizen wearing it has the same silhouette, and "they all look the same" is mostly a
 * statement about silhouette. These 14 models are the answer to it — 5 hair styles x 4 beards x
 * up to 5 headwear is <b>100 outlines</b> per person where there was one.
 *
 * <p>The head cube underneath is drawn as bald flesh on purpose (the generator gates it). That
 * costs nothing, because the relayed set was <b>already</b> bald: measured on the shipped file,
 * the head's top, back, left and right faces held zero non-flesh texels.
 *
 * <h2>Why every cube uses texOffs(0, 0)</h2>
 *
 * <p>So that the box table does not have to exist twice. The materials
 * ({@code npc_hair.png}, {@code npc_headwear.png}) are <b>uniform</b> — a strand pattern and a
 * cloth twill, 64x32, near-white and fully opaque — so a cube can sample any part of one and
 * needs no reserved rectangle. The moment a cube wanted its own region, that region would have to
 * be known both here and in {@code make_citizen_skins.py}, and two copies of one table is the
 * failure that left a retired villager layout inside {@code make_npc_textures --check} reporting
 * 126 phantom faults on every file in the mod.
 *
 * <p>Two consequences follow and both are enforced on the Python side: the material must be fully
 * opaque, since a transparent texel would punch a hole in an unpredictable cube face; and it must
 * be near-white, since the colour arrives as an ARGB multiply at render time.
 *
 * <h2>Concentric shells, so nothing is coplanar</h2>
 *
 * <p>The rig already stacks shells: the base cubes at 0, the garment's second layer at +0.25, the
 * {@code hat} cube at +0.5. These continue it — beard +0.3, hair +0.35, headwear +0.6 — so no two
 * surfaces ever share a plane and there is no z-fighting to tune. Headwear outside hair is also
 * the truthful order: a coif goes over the hair, not under it.
 *
 * <p>Coordinates are the head cube's own: it is {@code addBox(-4, -8, -4, 8, 8, 8)}, so y runs
 * from -8 at the crown to 0 at the chin and each of the face's eight rows is one unit of y. The
 * eyes are on row 4 (y -4..-3) and the mouth on row 6 (y -2..-1), which is what decides where a
 * hairline may stop and where a beard may start.
 */
public final class NpcHeadModels {

    private static final float HAIRLINE = 3.0F;    // rows 0..2 of the face: above the brow
    private static final CubeDeformation HAIR = new CubeDeformation(0.35F);
    private static final CubeDeformation BEARD = new CubeDeformation(0.30F);
    private static final CubeDeformation CLOTH = new CubeDeformation(0.60F);

    public static final int HAIR_COUNT = 5;
    public static final int BEARD_COUNT = 4;
    public static final int HEADWEAR_COUNT = 7;

    /** Hair styles. Index 0 is a crop and is what a citizen falls back to. */
    public static final ModelLayerLocation[] HAIR_LAYERS = layers("hair", HAIR_COUNT);

    /** Beards. Index 0 is none and has no model — the array slot stays null. */
    public static final ModelLayerLocation[] BEARD_LAYERS = layers("beard", BEARD_COUNT);

    /** Headwear. Index 0 is bare and has no model. 1..4 are a man's, 5..6 a woman's. */
    public static final ModelLayerLocation[] HEADWEAR_LAYERS = layers("headwear", HEADWEAR_COUNT);

    private static ModelLayerLocation[] layers(String kind, int n) {
        ModelLayerLocation[] out = new ModelLayerLocation[n];
        for (int i = 0; i < n; i++) {
            out[i] = new ModelLayerLocation(
                ResourceLocation.fromNamespaceAndPath(Ouat.MOD_ID, "npc_" + kind), "v" + i);
        }
        return out;
    }

    private NpcHeadModels() {
    }

    /**
     * Every definition to register, in the order of the arrays above. Null means "no model at this
     * index" — beard 0 and headwear 0, which are the absence of the thing.
     */
    @SuppressWarnings("unchecked")
    public static Supplier<LayerDefinition>[] hair() {
        return new Supplier[]{
            NpcHeadModels::hairCrop, NpcHeadModels::hairShaggy, NpcHeadModels::hairShoulder,
            NpcHeadModels::hairReceding, NpcHeadModels::hairTopknot};
    }

    @SuppressWarnings("unchecked")
    public static Supplier<LayerDefinition>[] beards() {
        return new Supplier[]{null, NpcHeadModels::beardStubble, NpcHeadModels::beardShort,
            NpcHeadModels::beardFull};
    }

    @SuppressWarnings("unchecked")
    public static Supplier<LayerDefinition>[] headwear() {
        return new Supplier[]{null, NpcHeadModels::coif, NpcHeadModels::strawHat,
            NpcHeadModels::hood, NpcHeadModels::cap, NpcHeadModels::veil, NpcHeadModels::wimple};
    }

    // ── the builder ──────────────────────────────────────────────────
    //
    // Every model is a single root part named "head", so the layer can bake it and render it
    // inside the parent model's own head transform without knowing anything about its contents.

    private static LayerDefinition of(CubeListBuilder cubes) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", cubes, PartPose.ZERO);
        // 64x32, the size of both materials. Every cube is at texOffs(0, 0) — see the class notes.
        return LayerDefinition.create(mesh, 64, 32);
    }

    // ── hair ─────────────────────────────────────────────────────────

    /** A crop: the crown and the forehead down to the brow, and nothing else. */
    private static LayerDefinition hairCrop() {
        return of(CubeListBuilder.create().texOffs(0, 0)
            .addBox(-4.0F, -8.0F, -4.0F, 8.0F, HAIRLINE, 8.0F, HAIR));
    }

    /** Shaggy: a thicker cap and a fringe hanging over the brow. */
    private static LayerDefinition hairShaggy() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, HAIRLINE, 8.0F,
                new CubeDeformation(0.5F))
            // The fringe is a front slab only, so it does not wrap the temples as well.
            .texOffs(0, 0).addBox(-4.0F, -5.4F, -4.3F, 8.0F, 2.0F, 0.6F, HAIR)
            // and a little over each ear
            .texOffs(0, 0).addBox(-4.4F, -5.4F, -4.0F, 0.6F, 2.0F, 8.0F, HAIR)
            .texOffs(0, 0).addBox(3.8F, -5.4F, -4.0F, 0.6F, 2.0F, 8.0F, HAIR));
    }

    /** To the shoulders: the cap, plus a fall down the back of the head and neck. */
    private static LayerDefinition hairShoulder() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, HAIRLINE, 8.0F, HAIR)
            .texOffs(0, 0).addBox(-4.0F, -5.0F, 2.6F, 8.0F, 7.0F, 1.4F, HAIR)
            .texOffs(0, 0).addBox(-4.3F, -5.0F, -1.0F, 0.6F, 5.0F, 5.0F, HAIR)
            .texOffs(0, 0).addBox(3.7F, -5.0F, -1.0F, 0.6F, 5.0F, 5.0F, HAIR));
    }

    /** Receding: set back from the forehead, so the scalp shows in front of it. */
    private static LayerDefinition hairReceding() {
        return of(CubeListBuilder.create().texOffs(0, 0)
            .addBox(-4.0F, -8.0F, -2.6F, 8.0F, 2.4F, 6.6F, new CubeDeformation(0.25F)));
    }

    /** Gathered and tied: the cap, and a knot on the crown. */
    private static LayerDefinition hairTopknot() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, HAIRLINE, 8.0F, HAIR)
            .texOffs(0, 0).addBox(-1.5F, -10.6F, -1.5F, 3.0F, 3.0F, 3.0F, CubeDeformation.NONE));
    }

    // ── beards ───────────────────────────────────────────────────────
    //
    // A beard starts BELOW the mouth or beside it, never over it. The mouth is face row 6, which
    // is y -2..-1, and the chin is row 7 at y -1..0. So a jaw band is y -1..0 and sideburns are
    // columns at x -4..-3 and 3..4 running up the cheeks. Covering the mouth would delete the one
    // feature the face has below the eyes.

    /** Stubble: the jaw line only. */
    private static LayerDefinition beardStubble() {
        return of(CubeListBuilder.create().texOffs(0, 0)
            .addBox(-4.0F, -1.0F, -4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.18F)));
    }

    /** Short: the jaw, and sideburns up past the mouth. */
    private static LayerDefinition beardShort() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -1.2F, -4.0F, 8.0F, 1.2F, 8.0F, BEARD)
            .texOffs(0, 0).addBox(-4.0F, -3.0F, -4.0F, 1.0F, 2.0F, 8.0F, BEARD)
            .texOffs(0, 0).addBox(3.0F, -3.0F, -4.0F, 1.0F, 2.0F, 8.0F, BEARD));
    }

    /** Full: jaw, sideburns, a moustache above the mouth and a hanging point below the chin. */
    private static LayerDefinition beardFull() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -1.4F, -4.0F, 8.0F, 1.4F, 8.0F, BEARD)
            .texOffs(0, 0).addBox(-4.0F, -3.4F, -4.0F, 1.0F, 2.4F, 8.0F, BEARD)
            .texOffs(0, 0).addBox(3.0F, -3.4F, -4.0F, 1.0F, 2.4F, 8.0F, BEARD)
            // the moustache: one course, above the mouth row, front face only
            .texOffs(0, 0).addBox(-2.5F, -2.6F, -4.4F, 5.0F, 0.8F, 0.6F, CubeDeformation.NONE)
            // and the point of the beard, below the chin
            .texOffs(0, 0).addBox(-2.0F, 0.2F, -3.6F, 4.0F, 2.6F, 3.0F, CubeDeformation.NONE));
    }

    // ── headwear ─────────────────────────────────────────────────────

    /** A coif: crown to the brow, flaps over the ears, and a band under the chin. */
    private static LayerDefinition coif() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F, CLOTH)
            .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 1.0F, 4.0F, 8.0F, new CubeDeformation(0.5F))
            .texOffs(0, 0).addBox(3.0F, -4.0F, -4.0F, 1.0F, 4.0F, 8.0F, new CubeDeformation(0.5F))
            .texOffs(0, 0).addBox(-4.0F, -0.8F, -4.0F, 8.0F, 1.2F, 8.0F,
                new CubeDeformation(0.45F)));
    }

    /** A straw hat: a wide brim sitting on the crown, and a low crown above it. */
    private static LayerDefinition strawHat() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-5.5F, -8.2F, -5.5F, 11.0F, 0.8F, 11.0F, CubeDeformation.NONE)
            .texOffs(0, 0).addBox(-3.0F, -11.0F, -3.0F, 6.0F, 3.0F, 6.0F, CubeDeformation.NONE));
    }

    /** A hood: a deep crown and a fall down the back. */
    private static LayerDefinition hood() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.2F, 8.0F,
                new CubeDeformation(0.8F))
            .texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 1.2F, 3.0F, 8.0F, new CubeDeformation(0.7F))
            .texOffs(0, 0).addBox(2.8F, -4.0F, -4.0F, 1.2F, 3.0F, 8.0F, new CubeDeformation(0.7F))
            .texOffs(0, 0).addBox(-4.0F, -4.0F, 3.0F, 8.0F, 5.0F, 1.6F, new CubeDeformation(0.5F)));
    }

    /** A cap with a turned-up brim. */
    private static LayerDefinition cap() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 2.6F, 8.0F,
                new CubeDeformation(0.7F))
            .texOffs(0, 0).addBox(-4.0F, -5.6F, -4.0F, 8.0F, 1.0F, 8.0F,
                new CubeDeformation(0.85F)));
    }

    /**
     * A veil: crown, a column of cloth framing each temple and jaw, and a fall to the shoulders.
     *
     * <p>Promoted from paint. The female set drew this on the {@code hat} cube, which worked but
     * bought no silhouette and occupied the one cube the shared face axis wants. As geometry it
     * gains an outline and frees the head. The rule that survives from the drawn version is that
     * the FRAME is what reads: the column of cloth beside the cheek is the only outline a face
     * has, and here it is a real edge rather than a tone chosen per complexion.
     */
    private static LayerDefinition veil() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F,
                new CubeDeformation(0.7F))
            .texOffs(0, 0).addBox(-4.8F, -4.0F, -4.0F, 1.0F, 6.0F, 8.0F, CubeDeformation.NONE)
            .texOffs(0, 0).addBox(3.8F, -4.0F, -4.0F, 1.0F, 6.0F, 8.0F, CubeDeformation.NONE)
            .texOffs(0, 0).addBox(-4.0F, -4.0F, 3.2F, 8.0F, 7.0F, 1.4F, new CubeDeformation(0.4F)));
    }

    /** A wimple: the veil, and the neck covered as well. */
    private static LayerDefinition wimple() {
        return of(CubeListBuilder.create()
            .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 4.0F, 8.0F,
                new CubeDeformation(0.7F))
            .texOffs(0, 0).addBox(-4.8F, -4.0F, -4.0F, 1.0F, 7.0F, 8.0F, CubeDeformation.NONE)
            .texOffs(0, 0).addBox(3.8F, -4.0F, -4.0F, 1.0F, 7.0F, 8.0F, CubeDeformation.NONE)
            .texOffs(0, 0).addBox(-4.0F, -4.0F, 3.2F, 8.0F, 8.0F, 1.4F, new CubeDeformation(0.4F))
            // the chin and throat, which is what makes a wimple a wimple
            .texOffs(0, 0).addBox(-4.0F, -1.0F, -4.0F, 8.0F, 2.4F, 8.0F,
                new CubeDeformation(0.55F)));
    }
}
