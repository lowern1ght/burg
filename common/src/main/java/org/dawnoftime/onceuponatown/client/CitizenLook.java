package org.dawnoftime.onceuponatown.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import org.dawnoftime.onceuponatown.Constants;
import org.dawnoftime.onceuponatown.entity.CitizenNames;
import org.dawnoftime.onceuponatown.entity.Npc;
import org.dawnoftime.onceuponatown.entity.citizen.Citizens;

import java.util.UUID;

/**
 * Who a citizen looks like: the single owner of every appearance roll on the shared rig.
 *
 * <h2>Why the appearance is spread across a texture, a tint and three cubes</h2>
 *
 * <p>The six male skins used to be one drawing multiplied by six palettes, and the complaint was
 * that the town read as one man repainted. Three measurements say why, and each one dictates
 * where a piece of the look had to go.
 *
 * <p><b>1. A texture cannot carry a silhouette on this rig.</b> Across the twelve skins the mod
 * shipped there were <b>three distinct alpha masks</b> — the six men shared ONE, the women had
 * two (veiled and bare). The outline of a citizen is the MODEL, not the skin; the only lever a
 * texture has is whether it paints the second-layer cubes at all, which is binary, and vanilla
 * uses it in just 4 of its 9 player skins. So hair, beard and headwear are <b>geometry</b>
 * ({@code NpcHeadModels}), because they are the only things that can change an outline.
 *
 * <p><b>2. The head cube was bald.</b> Measured on the shipped file: the head's top, back, left
 * and right faces held <b>zero</b> non-flesh texels. The relay that produced those skins carried
 * the villager's face over and lost the hair entirely in the 10&rarr;8 crop. So there was never
 * hair to preserve, and drawing the scalp as plain flesh under a cube of hair costs nothing.
 *
 * <p><b>3. The base texture cannot be tinted.</b> {@code LivingEntityRenderer.render} ends its
 * base pass with {@code model.renderToBuffer(pose, vc, light, i, flag1 ? 654311423 : -1)} — the
 * model colour is the literal {@code -1}, opaque white, with no per-entity hook. Every
 * {@link net.minecraft.client.renderer.entity.layers.RenderLayer} by contrast takes an ARGB int.
 * So <b>a body is a drawn file</b>, each drawn at its own full contrast rather than multiplied
 * down — the old dark variant kept only 20 luminance points of flesh modelling against 29 as
 * drawn — while <b>hair colour is a tint</b> on a layer.
 *
 * <p>The consequence worth stating: the beard is geometry rather than paint on the face for the
 * same reason. A painted beard could not follow the hair's tint — the base pass is that
 * {@code -1} — so a grey-haired man would have had a brown beard. It shares the hair's model,
 * texture and colour instead.
 *
 * <h2>A POOL OF DRAWN BODIES, NOT A CROSS PRODUCT</h2>
 *
 * <p>This class used to index 48 generated files as 4 complexions x 6 faces x 2 cuts. It now
 * indexes a pool of hand-drawn ones, and the arithmetic is what changed the design: 4 x 6 is
 * <b>24 visibly distinct bodies</b>, since the two cuts are the sexes and buy no variety inside
 * either. The larger figure that justified the cross product multiplied in the hair, beard and
 * headwear from {@code NpcHeadModels} — real variation, but contributed to any body equally. So
 * the cross product bought 24 bodies at the price of being unable to draw one of them, and it lost
 * the comparison the owner cares about: measured against 31 reference skins, a generated body
 * carried <b>17 distinct colours against their 139 median</b>, and drew no nose at all.
 *
 * <p>The drawn bodies are <b>people in their underclothes</b> — shift, hose, face, hands, feet —
 * and that is what keeps {@link org.dawnoftime.onceuponatown.client.model.layer.NpcClothesLayer}
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
    // *** THE POOL IS TWO PEOPLE. *** That is deliberate and it is not finished: the owner asked
    // to see the register land on one or two bodies before it was multiplied, so a town currently
    // draws from ONE man and ONE woman where the generated set gave 24. It is a large gain in how
    // a body reads and a loss in how many there are, and it is not a state to ship to players —
    // the roster is 12 to 20. Reverting is `git checkout` of this one file; the 48 generated
    // bodies are still on disk and still committed.

    private static final String[] MEN_BODIES = {"00"};
    private static final String[] WOMEN_BODIES = {"01"};

    /** Complexion families, kept as constants because the hair table is keyed on them. */
    private static final int LIGHT = 0, WARM = 1, OLIVE = 2, DARK = 3;

    /**
     * Which complexion family each drawn body belongs to, in the order of the arrays above.
     *
     * <p>A property of the person now rather than a roll, but it still has to be DECLARED,
     * because {@link #HAIR_BY_COMPLEXION} is keyed on it and that table is the reason a citizen
     * never comes out dark-complexioned with fair hair.
     */
    private static final int[] MEN_COMPLEXION = {WARM};
    private static final int[] WOMEN_COMPLEXION = {LIGHT};

    /** Hair styles, as cubes. Index 0 is the closest crop and is never absent. */
    public static final int HAIR_STYLES = 5;

    /** Beards, as cubes. Index 0 is none. Men only, and never on a child. */
    public static final int BEARDS = 4;

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
     * Headwear per sex, as indices into {@code NpcHeadModels.HEADWEAR}.
     *
     * <p>Gated rather than shared because the covering is the cut: a coif and a straw hat are a
     * working man's, a veil and a wimple are a married woman's. A hood is in both, being weather
     * rather than dress. Index 0 is bare in both, and bare has to stay available — six of six
     * women veiled read as a convent rather than a village when the female set was drawn, which
     * is why two of those six went bareheaded.
     */
    private static final int[] HEADWEAR_M = {0, 1, 2, 3, 4};   // bare, coif, straw hat, hood, cap
    private static final int[] HEADWEAR_W = {0, 5, 6, 3};      // bare, veil, wimple, hood

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
     */
    public static final Look BUILDER = new Look(false, 0, 0, MID_BROWN, 2, 0);

    private CitizenLook() {
    }

    /**
     * One citizen's whole appearance.
     *
     * @param female     which pool, and which cut — a shift to mid-thigh, or one to the ankle
     * @param body       index into that sex's drawn pool
     * @param hairStyle  index into {@code NpcHeadModels.HAIR}
     * @param hairColour ARGB multiplied into the hair material, shared by the beard
     * @param headwear   index into {@code NpcHeadModels.HEADWEAR}; 0 is bare
     * @param beard      index into {@code NpcHeadModels.BEARDS}; 0 is none
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
     * hair — and the layer scales it, see {@code NpcHeadLayer}.
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
}
