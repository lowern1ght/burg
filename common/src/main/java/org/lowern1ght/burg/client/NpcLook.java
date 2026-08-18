package org.lowern1ght.burg.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.lowern1ght.burg.Constants;
import org.lowern1ght.burg.entity.TownNpc;
import org.lowern1ght.burg.entity.citizen.Citizens;

import java.util.Map;

/**
 * How an entity on the shared rig should look, for entities that cannot tell us themselves.
 *
 * <p>{@code NpcModel} used to be typed on {@code Mob & TownNpc} and read the answers straight
 * off the entity. That stopped working the moment the cast included {@code minecraft:villager}:
 * a vanilla class cannot implement our interface without a mixin. So the questions moved here,
 * and this class answers them two ways — by delegation for anything of ours that implements
 * {@link TownNpc}, and by derivation for a villager, whose profession already says everything
 * the rig needs to know.
 */
public final class NpcLook {

    /**
     * Clothing per profession, drawn over the skin.
     *
     * <p>Vanilla draws its profession overlay on its own model and its own UV; ours is a
     * separate texture on the shared rig, so the whole cast stays one rig deep. A profession
     * with no entry here goes unclothed rather than borrowing someone else's trade.
     */
    private static final Map<String, String> CLOTHES = Map.of(
        "farmer", "farmer_clothes",
        "mason", "mason_clothes",
        "weaponsmith", "smith_clothes",
        "toolsmith", "smith_clothes",
        "armorer", "smith_clothes",
        "fletcher", "forester_clothes"
    );

    // ── WEALTH, AS A QUALITY TIER ON THE GARMENT ─────────────────────
    //
    // WHY WEALTH LIVES HERE AND NOT ON THE BODY. `LivingEntityRenderer.render` ends its base
    // pass with a hardcoded `-1` for the model colour, so the drawn body cannot be tinted at
    // all; every RenderLayer by contrast takes an ARGB int. That asymmetry is written up in
    // CitizenLook and it is the whole reason this axis is the garment's: the garment is a layer,
    // so it is the only part of a citizen whose colour can change while he is alive. And wealth
    // does change, which is why it is also the first axis in this system that is NOT derivable
    // from the UUID.
    //
    // WHAT SEPARATED RICH FROM POOR, AND IT WAS NOT A DIFFERENT GARMENT. It was dye saturation,
    // weave and trim. A poor man wore the colour the sheep grew; madder and woad cost money;
    // braid cost more. So this is one wardrobe at four grades of quality, NOT 7 trades x 4 tiers
    // of drawn file — which would be 28 textures to keep in step, and the recolour pipeline
    // exists precisely so that the hand-drawn mask is the only thing anyone draws.

    /**
     * The rungs of the wealth ladder. Four, and each gap is carried by a DIFFERENT mechanism —
     * which is the finding that settled the count.
     *
     * <ul>
     *   <li>{@link #FADED} &rarr; {@link #UNDYED} is <b>cleanliness</b>: value, not hue.
     *   <li>{@link #UNDYED} &rarr; {@link #DYED} is <b>dye saturation</b>.
     *   <li>{@link #DYED} &rarr; {@link #COSTLY} is <b>hue family plus braid</b>: the cheap dyes
     *       are warm, the ones needing woad or two baths are cool, and only the top rung is
     *       trimmed.
     * </ul>
     *
     * <p><b>Why not four bands of cloth tint.</b> Measured over the four garments a citizen can
     * actually wear ({@code farmer}, {@code mason}, {@code smith}, {@code forester}): three of
     * them sit at median luminance 61&ndash;73 and each carries only 10&ndash;11 distinct tones,
     * so the volume a multiply can reach is small. Four tint bands fit inside it only by
     * collapsing the variety WITHIN a band to 4.6&ndash;10 (the metric is below), against the
     * 15.5 the four shipped tints already achieve. Three bands keep it. The fourth rung is
     * therefore bought with the braid, which is new pixels on their own layer with their own
     * tint and so competes for none of that volume.
     */
    public static final int FADED = 0, UNDYED = 1, DYED = 2, COSTLY = 3;

    /** How many rungs. The modulus for anything that clamps a wealth value into the ladder. */
    public static final int WEALTH_RUNGS = 4;

    /**
     * Dye shifts multiplied into the clothing texture: one ROW PER WEALTH RUNG, and inside a row
     * one entry per {@link Citizens#tintOf} variant, so two farmers of the same means are still
     * not twins.
     *
     * <p><b>An eleventh-century range, and at the bottom that means no dye at all.</b> A peasant
     * wore the colour the sheep grew — cream, grey, moorit brown — because dyeing cost money and
     * labour a household in a sunken-floor house did not have. Madder is the dye that turns up on
     * ordinary cloth and weld gives a yellow-ochre; woad is affordable but not free, and green
     * needed DOUBLE-dyeing, woad over weld, which is why green is at the top rung and not on a
     * farmhand. That ordering — the cheapest dyestuff lowest — is the ladder.
     *
     * <p><b>The four tints this class used to hold are all still here, stratified rather than
     * discarded.</b> The three undyed fleeces stayed together as rung 1, which is the default, so
     * a town with no wealth wired to it looks exactly as it did. {@code 0xFFC08A63}, madder, moved
     * up one rung, because it is the one of the four that cost money. The ladder's TIGHTEST step
     * is that same pair — {@code 0xFFB0A498} to {@code 0xFFC08A63} on the forester — so the floor
     * and the narrowest rung of the ladder are one measurement, taken off cloth the mod already
     * shows side by side.
     *
     * <p><b>Rung 0 holds three tints and not four, and that is measured rather than lazy.</b> A
     * worn garment has to be darker than an undyed one and no darker than the darkest garment the
     * mod ships ({@code soldier_veteran}, median luminance 38.2, less this repo's measured
     * 7-point invisibility threshold). Nothing in an honest wear hue — mud, dust, rust, water
     * stain, mildew — fits a fourth entry in that window; the only candidate the search found was
     * a saturated green, which is the most expensive colour in this file. So the row is three and
     * {@link Citizens#tintOf(net.minecraft.world.entity.npc.Villager, int)} takes the row's own
     * length rather than folding a 4-way roll into it, which would have given entry 0 half the
     * town.
     *
     * <p>All of them keep their dominant channel high on purpose. These MULTIPLY a drawn texture,
     * so a tint whose brightest channel is already dark leaves the garment a smear: the gate is
     * that the tinted median luminance stays above 31, and the numbers land at 31.4 at worst.
     */
    private static final int[][] TINTS_BY_WEALTH = {
        {
            // rung 0 — FADED. Wear, not dye: what a garment becomes, not what it was made as.
            0xFF806154,   // ground-in mud
            0xFF7E939E,   // a cold water stain, the one that is not warm
            0xFF8F8467,   // sun-rotted, the colour gone out of it
        },
        {
            // rung 1 — UNDYED, and this row is the four tints this class shipped, unchanged
            // except that madder left it. THE DEFAULT: a town with no wealth wired looks as before.
            0xFFFFFFFF,   // as drawn — undyed cream fleece
            0xFFD8C9A8,   // undyed, the yellower fleece
            0xFFB0A498,   // undyed grey-brown, a moorit sheep
            0xFFDBDBDB,   // a grey sheep, and the only one of the four with no hue at all
        },
        {
            // rung 2 — DYED, in the dyestuffs a household could buy.
            0xFFFF6352,   // madder, at strength
            0xFFFACD46,   // weld — a yellow-ochre, the cheapest dye there was
            0xFFC08A63,   // madder as this class shipped it, one rung up from where it was
            0xFFF58497,   // madder in a weak bath, the second dyeing off one pot of roots
        },
        {
            // rung 3 — COSTLY, and trimmed. Woad, or two dye baths, or both.
            0xFF4D94FF,   // woad
            0xFF77EB54,   // green — woad over weld, so two dyeings and twice the price
            0xFFFA377E,   // a deep madder, the strong first bath rather than the second
            0xFFDE59EB,   // murrey, madder over woad — the other double-dye
        },
    };

    /**
     * The braid at the top rung, paired with the cloth of the same index.
     *
     * <p>Its own tint and not the cloth's, which is the point of the trim being its own layer: a
     * gold braid on a woad gown is what wealth looked like, and one tint over both would only
     * give a lighter edge of the same colour. {@code citizen_trim.png} is drawn in near-white
     * greys for the same reason {@code npc_hair.png} is — a multiply can only darken, so the
     * drawn file has to start bright or the tint has no range to work in.
     */
    private static final int[] TRIM_TINTS = {
        0xFFD8A840,   // gold, on woad
        0xFFD8B830,   // a weld silk band, on green
        0xFF5A78C8,   // a woad silk band, on deep madder — a tablet-woven band, not metal
        0xFFD8A840,   // gold again, on murrey
    };
    // Three braid colours over four pairings, and the fourth candidate was dropped ON THE SHEET
    // rather than by a number: a madder-red band on the green cloth cleared every gate and read
    // as red-on-green. Cream, silver and pewter went the same way for the opposite reason — see
    // the braid floor in `draw_citizens.py`.

    /** Where the braid is drawn. One file, because all eight garments share ONE alpha mask. */
    private static final String TRIM = "citizen_trim";

    /**
     * The rung a citizen is on when nobody has said otherwise.
     *
     * <p>{@link #UNDYED}, because that row IS the range this class shipped: a build with no
     * wealth wired to it renders exactly as it did before wealth existed.
     */
    public static final int DEFAULT_WEALTH = UNDYED;

    private NpcLook() {
    }

    /**
     * <b>*** THE SEAM. ***</b> How well off this entity is, as a rung of the wealth ladder.
     *
     * <p>This is the one method to rewire when the population model's wealth reaches the client,
     * and it is deliberately the only place that decides. Everything downstream of it takes the
     * rung as a parameter, so a caller that already HAS the value — a screen, a test, the contact
     * sheet in {@code tools/draw_citizens.py} — never comes through here at all.
     *
     * <p>It returns a constant today, and that is not a placeholder waiting to become a roll.
     * <b>Wealth is the first axis in this system that is not derivable from the UUID</b>, because
     * it changes while a person is alive: an id-hash would be a number that looks like wealth,
     * never moves, and would have to be unlearnt. Every other axis stays rolled from the id and
     * needs no syncing; this one needs a synced summary and does not have one yet.
     */
    public static int wealthOf(Mob mob) {
        return DEFAULT_WEALTH;
    }

    /**
     * What this body is doing, for the model.
     *
     * <p>Anything that is not ours stands: a vanilla villager has no pose of ours to report, and
     * guessing one from its brain would be a second, worse copy of a decision the server already
     * makes for our own people.
     */
    public static org.lowern1ght.burg.entity.NpcPose poseOf(Mob mob) {
        if (mob instanceof org.lowern1ght.burg.entity.Npc npc) return npc.getNpcPose();
        return org.lowern1ght.burg.entity.NpcPose.STANDING;
    }

    /** Reading the town plan: both hands occupied, head tilted down. Builders only. */
    public static boolean isReading(Mob mob) {
        return mob instanceof TownNpc npc && npc.isReading();
    }

    /**
     * Arms folded across the chest.
     *
     * <p>For a villager this is vanilla's own tell for unemployed, and true for the same
     * reason — it is the pose vanilla itself uses, so a jobless citizen reads the way a player
     * already expects a jobless villager to read.
     */
    public static boolean isCrossingArms(Mob mob) {
        if (mob instanceof TownNpc npc) return npc.isCrossingArms();
        if (mob instanceof Villager villager) {
            return villager.getVillagerData().getProfession() == VillagerProfession.NONE;
        }
        return false;
    }

    /** The clothing overlay, or {@code null} for bare. */
    public static ResourceLocation clothes(Mob mob) {
        if (mob instanceof TownNpc npc) return npc.clothesTexture();
        if (mob instanceof Villager villager) {
            String key = CLOTHES.get(villager.getVillagerData().getProfession().name());
            if (key == null) return null;
            return ResourceLocation.fromNamespaceAndPath(
                Constants.MOD_ID, "textures/entity/npc/" + key + ".png");
        }
        return null;
    }

    /**
     * ARGB multiplied into the clothing, so two farmers are not twins.
     *
     * <p>Opaque white for anything that is not a citizen — and opaque is the operative word.
     * This argument used to be handed {@code getOverlayCoords(...)}, a leftover from the
     * 1.20.1 signature where the tail of the parameter list was three float colour channels
     * rather than one packed int. The overlay constant is {@code 0x000A0000}, whose alpha byte
     * is zero, so every garment in the mod was being drawn fully transparent — which is the
     * real reason the citizens turned up looking like nothing but recoloured skin.
     */
    public static int clothesTint(Mob mob) {
        return clothesTint(mob, wealthOf(mob));
    }

    /**
     * The same, for a caller that already knows how well off the entity is.
     *
     * <p>Which row of {@link #TINTS_BY_WEALTH}; which entry inside the row is still the citizen's
     * own id-roll, so stratifying by wealth costs none of the variety the roll already bought.
     */
    public static int clothesTint(Mob mob, int wealthRung) {
        if (mob instanceof Villager villager && Citizens.isCitizen(villager)) {
            int[] row = TINTS_BY_WEALTH[Math.floorMod(wealthRung, WEALTH_RUNGS)];
            return row[Citizens.tintOf(villager, row.length)];
        }
        return 0xFFFFFFFF;
    }

    /** The braid overlay, or {@code null} — which is every rung but the top one. */
    public static ResourceLocation trim(Mob mob) {
        return trim(mob, wealthOf(mob));
    }

    public static ResourceLocation trim(Mob mob, int wealthRung) {
        if (Math.floorMod(wealthRung, WEALTH_RUNGS) != COSTLY) return null;
        if (clothes(mob) == null) return null;   // no garment, nothing to edge
        return ResourceLocation.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/entity/npc/" + TRIM + ".png");
    }

    /** ARGB multiplied into the braid. Paired with the cloth entry, not rolled separately. */
    public static int trimTint(Mob mob) {
        return trimTint(mob, wealthOf(mob));
    }

    public static int trimTint(Mob mob, int wealthRung) {
        if (mob instanceof Villager villager && Citizens.isCitizen(villager)) {
            int[] row = TINTS_BY_WEALTH[Math.floorMod(wealthRung, WEALTH_RUNGS)];
            return TRIM_TINTS[Math.floorMod(Citizens.tintOf(villager, row.length),
                                            TRIM_TINTS.length)];
        }
        return 0xFFFFFFFF;
    }
}
