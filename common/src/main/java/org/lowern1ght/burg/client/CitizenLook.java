package org.lowern1ght.burg.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.entity.CitizenNames;
import org.lowern1ght.burg.entity.Npc;
import org.lowern1ght.burg.entity.citizen.Citizens;

import java.util.UUID;

/**
 * Who a citizen looks like: the single owner of every appearance roll on the shared rig.
 *
 * <h2>Why the appearance is spread across a drawn file, a painting and a tint</h2>
 *
 * <p>The six male skins used to be one drawing multiplied by six palettes, and the complaint was
 * that the town read as one man repainted. Three measurements say why, and each one dictates
 * where a piece of the look had to go.
 *
 * <p><b>1. A texture CARRIES the silhouette, and the earlier claim here that it could not was
 * wrong.</b> This paragraph used to read "the outline of a citizen is the MODEL, not the skin", on
 * the evidence that the twelve skins the mod shipped had only <b>three distinct alpha masks</b>
 * between them. That measurement is real and says something narrower than it was made to say: those
 * twelve never used the lever, not that the lever is absent. The number that settles it is the
 * owner's own reference set — <b>31 of 31 use the head's second layer</b>, which is how every hat,
 * hood and long hair in Minecraft is drawn, and vanilla ships no hair models at all. So hair, beard
 * and headwear are <b>paint on the {@code hat} cube</b> ({@code NpcHairLayer}), whose alpha carves
 * the outline. The 42 baked cubes that briefly did this job are retired on disk, unreferenced; they
 * also cost a black screen, which paint cannot do.
 *
 * <p>The one thing the geometry could do and paint cannot: <b>project past the head</b>. A shell
 * inflated half a block cannot carry a brim, so the straw hat is a brimless cap with a band.
 *
 * <p><b>2. The head cube was bald.</b> Measured on the shipped file: the head's top, back, left
 * and right faces held <b>zero</b> non-flesh texels. The relay that produced those skins carried
 * the villager's face over and lost the hair entirely in the 10&rarr;8 crop. So there was never
 * hair to preserve, and drawing the scalp as plain flesh under a SHELL of painted hair costs
 * nothing — and it is still the right thing under paint, because the shell's transparent cells are
 * exactly where a scalp should show.
 *
 * <p><b>3. The base texture cannot be tinted.</b> {@code LivingEntityRenderer.render} ends its
 * base pass with {@code model.renderToBuffer(pose, vc, light, i, flag1 ? 654311423 : -1)} — the
 * model colour is the literal {@code -1}, opaque white, with no per-entity hook. Every
 * {@link net.minecraft.client.renderer.entity.layers.RenderLayer} by contrast takes an ARGB int.
 * So <b>a body is a drawn file</b>, each drawn at its own full contrast rather than multiplied
 * down — the old dark variant kept only 20 luminance points of flesh modelling against 29 as
 * drawn — while <b>hair colour is a tint</b> on a layer.
 *
 * <p>The consequence worth stating: the beard is on the HAIR'S LAYER rather than painted into the
 * face, for the same reason. A beard in the body texture could not follow the hair's tint — the base
 * pass is that {@code -1} — so a grey-haired man would have had a brown beard. It is its own
 * painting on the same cube, drawn in the same pass order and multiplied by the same colour.
 *
 * <h2>A POOL OF DRAWN BODIES, NOT A CROSS PRODUCT</h2>
 *
 * <p>This class used to index 48 generated files as 4 complexions x 6 faces x 2 cuts. It now
 * indexes a pool of hand-drawn ones, and the arithmetic is what changed the design: 4 x 6 is
 * <b>24 visibly distinct bodies</b>, since the two cuts are the sexes and buy no variety inside
 * either. The larger figure that justified the cross product multiplied in the hair, beard and
 * headwear, which is real variation but is contributed to any body equally. So
 * the cross product bought 24 bodies at the price of being unable to draw one of them, and it lost
 * the comparison the owner cares about: measured against 31 reference skins, a generated body
 * carried <b>17 distinct colours against their 139 median</b>, and drew no nose at all.
 *
 * <p>The drawn bodies are <b>people in their underclothes</b> — shift, hose, face, hands, feet —
 * and that is what keeps {@link org.lowern1ght.burg.client.model.layer.NpcClothesLayer}
 * alive. One tunic file per trade over any body is what tells a farmer from a smith; draw finished
 * characters instead and the tunic over them is a mess, the layer has to go, and the pool has to
 * cover 7 professions x 2 sexes before any variety inside a trade. Twelve to twenty bodies is
 * enough with the layer, because each multiplies by 7 garments and by ~100 head outlines.
 *
 * <p>{@code tools/draw_citizens.py} owns the drawn files and gates them: the complexion span, the
 * luminance-weighted face separation, the garment mask coupling, a nose with a lit bridge, and a
 * floor on distinct colours. The 48 generated files stay on disk, unreferenced, the way the 12
 * relayed skins before them were left.
 *
 * <h2>The rolls</h2>
 *
 * <p>All of them go through {@link CitizenNames#variant}, which mixes a per-axis salt into the
 * UUID before hashing. That is not decoration: taking the face as {@code h % 6} and the tint as
 * {@code h % 4} off one hash shares the low bit between them, so half the combinations never
 * occur and the town comes out in visible pairs. Every axis therefore has its own salt, and the
 * salts are unique across this class and {@link Citizens}.
 *
 * <p>Every axis is derivable on the client from the UUID alone, so none of it needs syncing. The
 * face goes through {@link Citizens#faceOf} rather than a raw roll so that a server-side override
 * on the attachment — a chief given a chosen face — still wins.
 */
public final class CitizenLook {

    // ── the drawn pool ───────────────────────────────────────────────
    //
    // One entry per hand-drawn file, split by sex because the cut is the sex: a shift to
    // mid-thigh over hose against a shift to the ankle. The slug is the filename's, so adding a
    // person is one string here and one entry in `tools/draw_citizens.py`.
    //
    // Every entry is a person drawn separately, and `tools/draw_citizens.py` proves that rather
    // than asserting it: it compares every pair SYMBOLICALLY, palette ignored, and gates the share
    // of cells that differ. A repaint of one drawing scores 0. These score 65% and up.
    //
    // Both arrays carry all four complexion families, so a town cannot come out one colour whichever
    // way the sex flip lands — and the DARK ones are drawn rather than multiplied down, which is the
    // measurement the whole approach rests on: 36 luminance points of flesh modelling against 20.

    private static final String[] MEN_BODIES = {"00", "02", "04", "06", "08", "10", "12"};
    private static final String[] WOMEN_BODIES = {"01", "03", "05", "07", "09", "11", "13"};

    /** Complexion families, kept as constants because the hair table is keyed on them. */
    private static final int LIGHT = 0, WARM = 1, OLIVE = 2, DARK = 3;

    /**
     * Which complexion family each drawn body belongs to, in the order of the arrays above.
     *
     * <p>A property of the person now rather than a roll, but it still has to be DECLARED,
     * because {@link #HAIR_BY_COMPLEXION} is keyed on it and that table is the reason a citizen
     * never comes out dark-complexioned with fair hair.
     */
    private static final int[] MEN_COMPLEXION =
        {WARM, DARK, OLIVE, LIGHT, WARM, OLIVE, DARK};
    private static final int[] WOMEN_COMPLEXION =
        {LIGHT, WARM, OLIVE, DARK, WARM, LIGHT, DARK};

    /** Hair styles, as PAINTINGS on the {@code hat} cube. Index 0 is the crop and is never absent. */
    public static final int HAIR_STYLES = 5;

    /** Beards, as paintings. Index 0 is none and has no file. Men only, and never on a child. */
    public static final int BEARDS = 4;

    /** Coverings, as paintings. Index 0 is bare and has no file. */
    public static final int HEADWEAR_KINDS = 6;

    // Salts. 0 and 1 belong to Citizens (FACE_SALT, TINT_SALT); these continue the sequence and
    // must stay unique across both classes or two axes become one. 2 belonged to the retired
    // complexion axis and is left unused rather than recycled: a salt that changes meaning
    // silently re-rolls every citizen in every existing save.
    private static final int HAIR_STYLE_SALT = 3;
    private static final int HAIR_COLOUR_SALT = 4;
    private static final int HEADWEAR_SALT = 5;
    private static final int BEARD_SALT = 6;

    /**
     * Hair colours, as ARGB multiplied into the neutral strand material.
     *
     * <p>Near-black through fair, plus grey. They multiply a near-white drawn texture, the same
     * arrangement {@link NpcLook#clothesTint} uses for cloth.
     */
    private static final int BLACK = 0xFF231F1C;
    private static final int DARK_BROWN = 0xFF3D2D24;
    private static final int MID_BROWN = 0xFF5C4033;
    private static final int FAIR = 0xFFA8834E;
    private static final int GREY = 0xFF8F8A83;

    /**
     * Which hair colours each complexion family may roll, and this table is the point.
     *
     * <p>Rolling colour freely against complexion would give one citizen in twenty the dark
     * complexion with fair hair, and a 1-in-20 combination does not read as variety — it reads as
     * a bug, once, to the one player who sees it. Grey is in every set because it is age rather
     * than colouring, and age is the one of these that is not inherited.
     *
     * <p>Three per family, so the roll is uniform whichever body came up.
     */
    private static final int[][] HAIR_BY_COMPLEXION = {
        {MID_BROWN, FAIR, GREY},          // light
        {DARK_BROWN, MID_BROWN, GREY},    // warm
        {BLACK, DARK_BROWN, GREY},        // olive
        {BLACK, DARK_BROWN, GREY},        // dark
    };

    /**
     * Headwear per sex, as indices into the painted covering pool.
     *
     * <p>Gated rather than shared because the covering is the cut: a coif and a straw cap are a
     * working man's, a veil and a wimple are a married woman's. A hood is in both, being weather
     * rather than dress. Index 0 is bare in both, and bare has to stay available — six of six
     * women veiled read as a convent rather than a village when the female set was drawn, which
     * is why two of those six went bareheaded.
     */
    private static final int[] HEADWEAR_M = {0, 1, 2, 3};   // bare, coif, straw cap, hood
    private static final int[] HEADWEAR_W = {0, 4, 5, 3};   // bare, veil, wimple, hood

    /**
     * The colour of each covering, and it is per KIND rather than per person.
     *
     * <p>These are materials and not dyes: linen is bleached or it is not, straw is straw. They
     * multiply a near-white painting, the same arrangement {@link NpcLook} uses for cloth, and they
     * stay close to white for the reason that table gives — a strong tint stops reading as cloth and
     * starts reading as a team colour. Index 0 is bare and is never drawn.
     */
    private static final int[] HEADWEAR_TINT = {
        0xFFFFFFFF,   // 0 bare, unused
        0xFFE8E2D2,   // 1 coif — unbleached linen
        0xFFD8BE7E,   // 2 straw cap
        0xFF9A9084,   // 3 hood — undyed grey-brown wool
        0xFFEFEADA,   // 4 veil — bleached linen, the one thing a household bleached
        0xFFE2DDC9,   // 5 wimple
    };

    /**
     * The builder's look, chosen rather than rolled.
     *
     * <p>He needs choosing because of an accident worth recording: {@code citizen_skin_0.png} and
     * {@code default_skin.png} were <b>the same file</b> — git blob {@code 4f5ef400} for both,
     * because the old pipeline's first variant was the identity transform. So the most-seen NPC in
     * the game was silently on the citizen path, wearing the citizen set's worst artefact: a
     * torso of thirteen desaturated greys at median luminance 98, which is the real reason he read
     * as wearing a black cloak. It was never a bug in the garment code.
     *
     * <p>He is the drawn man — the weathered warm complexion — with a coif and a short beard, and
     * {@code builder_clothes.png} goes over the top of him like any other trade. He needs no
     * special case beyond being chosen rather than rolled. {@code default_skin.png} stays on disk
     * untouched.
     *
     * <p>The constant used to say {@code (…, 2, 0)} — covering 2, no beard — while the sentence
     * above claimed a coif and a short beard. It now says what it means.
     */
    public static final Look BUILDER = new Look(false, 0, 0, MID_BROWN, 1, 2);

    private CitizenLook() {
    }

    /**
     * One citizen's whole appearance.
     *
     * @param female     which pool, and which cut — a shift to mid-thigh, or one to the ankle
     * @param body       index into that sex's drawn pool
     * @param hairStyle  index into the painted hair pool; 0 is a crop and is never absent
     * @param hairColour ARGB multiplied into the hair material, shared by the beard
     * @param headwear   index into the painted covering pool; 0 is bare and has no file
     * @param beard      index into the painted beard pool; 0 is none and has no file
     */
    public record Look(boolean female, int body, int hairStyle,
                       int hairColour, int headwear, int beard) {
    }

    /** How many drawn bodies this sex has. The modulus for every index into the pool. */
    public static int bodies(boolean female) {
        return (female ? WOMEN_BODIES : MEN_BODIES).length;
    }

    /**
     * Derive the look from the entity's own id.
     *
     * <p><b>A child has no beard and no headwear.</b> {@code Npc} extends {@code AgeableMob} and
     * {@code getBreedOffspring} returns one, so babies are real and reachable, and a bearded
     * infant in a coif is exactly the sort of thing that ships. Hair is kept — children have
     * hair, and a painting on the `hat` cube follows the head at any scale.
     */
    public static Look of(Mob mob) {
        boolean young = mob.isBaby();
        // The builder is authored, not rolled — see BUILDER. Rolling him would have put his hair
        // and beard on a UUID while his body stayed fixed, which is two people in one figure.
        if (mob instanceof Npc) {
            return young ? new Look(false, BUILDER.body(), BUILDER.hairStyle(),
                                    BUILDER.hairColour(), 0, 0)
                         : BUILDER;
        }
        UUID id = mob.getUUID();
        boolean female = mob instanceof Villager v ? Citizens.isFemale(v)
                                                   : CitizenNames.isFeminine(id);
        // WHICH DRAWN BODY, through Citizens.faceOf so a server-set override on the attachment
        // still wins — a chief given a chosen body keeps it. That call is what the retired face
        // axis used, and it is reused rather than replaced precisely so the override survives:
        // the face is now part of the body it was folded into, so "which face" and "which body"
        // have become one question and there is no reason to publish a second answer.
        int pool = bodies(female);
        int chosen = mob instanceof Villager v ? Citizens.faceOf(v, pool)
                                              : CitizenNames.variant(id, 0, pool);
        int style = CitizenNames.variant(id, HAIR_STYLE_SALT, HAIR_STYLES);

        int[] families = female ? WOMEN_COMPLEXION : MEN_COMPLEXION;
        int[] palette = HAIR_BY_COMPLEXION[families[Math.floorMod(chosen, pool)]];
        int colour = palette[CitizenNames.variant(id, HAIR_COLOUR_SALT, palette.length)];

        int[] hats = female ? HEADWEAR_W : HEADWEAR_M;
        int headwear = young ? 0 : hats[CitizenNames.variant(id, HEADWEAR_SALT, hats.length)];
        int beard = (female || young) ? 0 : CitizenNames.variant(id, BEARD_SALT, BEARDS);

        return new Look(female, Math.floorMod(chosen, pool), style, colour, headwear, beard);
    }

    // Resolved once. The base pass batches per texture, so the pool's size is also the worst case
    // for how many entity draw batches a crowd can cost — two today, twelve to twenty when the
    // roster is drawn, against 48 for the generated set it replaces.
    private static final ResourceLocation[][] BODIES = {
        paths(MEN_BODIES), paths(WOMEN_BODIES),
    };

    private static ResourceLocation[] paths(String[] slugs) {
        ResourceLocation[] out = new ResourceLocation[slugs.length];
        for (int i = 0; i < slugs.length; i++) {
            out[i] = ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID, "textures/entity/npc/citizen_body_" + slugs[i] + ".png");
        }
        return out;
    }

    /** The drawn body for a look. */
    public static ResourceLocation body(Look look) {
        ResourceLocation[] pool = BODIES[look.female() ? 1 : 0];
        return pool[Math.floorMod(look.body(), pool.length)];
    }

    public static ResourceLocation body(Mob mob) {
        return body(of(mob));
    }

    // ── the painted head: hair, beard, covering ──────────────────────
    //
    // Three small textures over the `hat` cube, resolved once. Index 0 of a beard or a covering is
    // the ABSENCE of the thing and has no file at all — which is the whole difference from the
    // geometry this replaces, where an absent variant was a null in an array that a registration
    // loop then tried to bake, and that was the black screen.

    private static final ResourceLocation[] HAIR = numbered("hair", HAIR_STYLES, 0);
    private static final ResourceLocation[] BEARD = numbered("beard", BEARDS, 1);
    private static final ResourceLocation[] HEADWEAR = numbered("headwear", HEADWEAR_KINDS, 1);

    private static ResourceLocation[] numbered(String kind, int count, int first) {
        ResourceLocation[] out = new ResourceLocation[count];
        for (int i = first; i < count; i++) {
            out[i] = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, String.format(
                "textures/entity/npc/citizen_%s_%02d.png", kind, i));
        }
        return out;
    }

    /** Hair. Never {@code null} — index 0 is a crop, not baldness. */
    public static ResourceLocation hair(Look look) {
        return HAIR[Math.floorMod(look.hairStyle(), HAIR.length)];
    }

    /** The beard, or {@code null} for a clean chin. Takes the HAIR's colour, not its own. */
    public static ResourceLocation beard(Look look) {
        return BEARD[Math.floorMod(look.beard(), BEARD.length)];
    }

    /** The covering, or {@code null} for a bare head. */
    public static ResourceLocation headwear(Look look) {
        return HEADWEAR[Math.floorMod(look.headwear(), HEADWEAR.length)];
    }

    /** ARGB multiplied into the covering. Per kind, because linen and straw are materials. */
    public static int headwearTint(Look look) {
        return HEADWEAR_TINT[Math.floorMod(look.headwear(), HEADWEAR_TINT.length)];
    }
}
