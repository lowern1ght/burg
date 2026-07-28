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
 * So <b>complexion is a drawn file</b> (four of them, each drawn at its own full contrast rather
 * than multiplied down — the old dark variant kept only 20 luminance points of flesh modelling
 * against 29 as drawn) while <b>hair colour is a tint</b> on a layer.
 *
 * <p>The consequence worth stating: the beard is geometry rather than paint on the face for the
 * same reason. A painted beard could not follow the hair's tint — the base pass is that
 * {@code -1} — so a grey-haired man would have had a brown beard. It shares the hair's model,
 * texture and colour instead.
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

    /** Drawn complexion files. Four, not six: see the class notes on why they are drawn. */
    public static final int COMPLEXIONS = 4;

    /**
     * Face overlays folded into the body texture at build time.
     *
     * <p>Six, and six is a measurement rather than a round number. Over vanilla's nine human
     * skins on this same layout, a face spends a <b>median of 16 ink texels</b> of the 64 on the
     * head front (range 4..46). Ten faces was the first proposal and 16 texels will not carry
     * ten distinguishable ones. The generator gates the weakest pair at 80 luminance points of
     * separation and currently measures 123.
     */
    public static final int FACES = 6;

    /** Hair styles, as cubes. Index 0 is the closest crop and is never absent. */
    public static final int HAIR_STYLES = 5;

    /** Beards, as cubes. Index 0 is none. Men only, and never on a child. */
    public static final int BEARDS = 4;

    // Salts. 0 and 1 belong to Citizens (FACE_SALT, TINT_SALT); these continue the sequence and
    // must stay unique across both classes or two axes become one.
    private static final int COMPLEXION_SALT = 2;
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
     * Which hair colours each complexion may roll, and this table is the point.
     *
     * <p>Rolling colour freely against complexion would give one citizen in twenty the dark
     * complexion with fair hair, and a 1-in-20 combination does not read as variety — it reads as
     * a bug, once, to the one player who sees it. Grey is in every set because it is age rather
     * than colouring, and age is the one of these that is not inherited.
     *
     * <p>Three per complexion, so the roll is uniform whichever complexion came up.
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
     * <p>He is now deliberately a warm complexion with the {@code lined} face and a short beard —
     * a man who has been working — and {@code builder_clothes.png} goes over the top of a torso
     * drawn for it. {@code default_skin.png} stays on disk untouched.
     */
    public static final Look BUILDER = new Look(false, 1, 4, 0, MID_BROWN, 2, 0);

    private CitizenLook() {
    }

    /**
     * One citizen's whole appearance.
     *
     * @param female     which cut — tunic to the knee, or gown to the ankle
     * @param complexion index into the drawn complexion files
     * @param face       index into the drawn faces
     * @param hairStyle  index into {@code NpcHeadModels.HAIR}
     * @param hairColour ARGB multiplied into the hair material, shared by the beard
     * @param headwear   index into {@code NpcHeadModels.HEADWEAR}; 0 is bare
     * @param beard      index into {@code NpcHeadModels.BEARDS}; 0 is none
     */
    public record Look(boolean female, int complexion, int face, int hairStyle,
                       int hairColour, int headwear, int beard) {
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
            return young ? new Look(false, BUILDER.complexion(), BUILDER.face(),
                                    BUILDER.hairStyle(), BUILDER.hairColour(), 0, 0)
                         : BUILDER;
        }
        UUID id = mob.getUUID();
        boolean female = mob instanceof Villager v ? Citizens.isFemale(v)
                                                   : CitizenNames.isFeminine(id);
        int complexion = CitizenNames.variant(id, COMPLEXION_SALT, COMPLEXIONS);
        // Through Citizens so a server-set override on the attachment still wins.
        int face = mob instanceof Villager v ? Citizens.faceOf(v)
                                             : CitizenNames.variant(id, 0, FACES);
        int style = CitizenNames.variant(id, HAIR_STYLE_SALT, HAIR_STYLES);

        int[] palette = HAIR_BY_COMPLEXION[complexion];
        int colour = palette[CitizenNames.variant(id, HAIR_COLOUR_SALT, palette.length)];

        boolean baby = young;
        int[] hats = female ? HEADWEAR_W : HEADWEAR_M;
        int headwear = baby ? 0 : hats[CitizenNames.variant(id, HEADWEAR_SALT, hats.length)];
        int beard = (female || baby) ? 0 : CitizenNames.variant(id, BEARD_SALT, BEARDS);

        return new Look(female, complexion, Math.floorMod(face, FACES), style, colour,
                        headwear, beard);
    }

    // Resolved once. The base pass batches per texture, so this array's size is also the worst
    // case for how many entity draw batches a crowd can cost: up to 48 instead of the old 12.
    private static final ResourceLocation[][][] BODIES =
        new ResourceLocation[2][COMPLEXIONS][FACES];

    static {
        for (int s = 0; s < 2; s++) {
            for (int c = 0; c < COMPLEXIONS; c++) {
                for (int f = 0; f < FACES; f++) {
                    BODIES[s][c][f] = ResourceLocation.fromNamespaceAndPath(
                        Constants.MOD_ID, String.format(
                            "textures/entity/npc/citizen_%s_c%d_f%d.png",
                            s == 1 ? "w" : "m", c, f));
                }
            }
        }
    }

    /** The body texture for a look: the build-time cross product of complexion and face. */
    public static ResourceLocation body(Look look) {
        return BODIES[look.female() ? 1 : 0][look.complexion()][look.face()];
    }

    public static ResourceLocation body(Mob mob) {
        return body(of(mob));
    }
}
