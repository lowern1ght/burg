package org.dawnoftime.onceuponatown.entity;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * A name for a citizen, derived from its UUID so it never changes and never needs saving.
 *
 * <p>Cheapest uniqueness in the mod, and the one that carries the most weight later. A dead
 * "resident" is a number going down; a dead <i>Hedda Ashcroft</i> is a loss the player can
 * feel, and that is the difference between a raid that costs you statistics and one that
 * costs you people. Same for the chief: diplomacy between two chiefs needs two names before
 * it needs anything else.
 *
 * <p>Built from syllables rather than a fixed list: 24 x 16 x 12 given names against 14 x 10
 * families is more combinations than a town will ever hold, out of a table small enough to
 * read. The vocabulary is deliberately plain and a little archaic — a plains village, not a
 * fantasy court — and it matches the buildings, which are oak and cobble and nothing grander
 * until they earn it.
 *
 * <p>Derived from the UUID and NOT stored: the name is a pure function of an id Minecraft
 * already persists, so it survives a reload for free and two citizens can never quietly
 * swap names when a save is reordered. Move this to a datapack when it wants translating.
 */
public final class CitizenNames {

    private static final List<String> HEAD = List.of(
        "Ald", "Bern", "Cuth", "Dun", "Ead", "Fren", "Gar", "Hed",
        "Ing", "Kel", "Lud", "Mer", "Nor", "Os", "Rand", "Sig",
        "Thur", "Ulf", "Wal", "Yrs", "Brun", "Cerd", "Hroth", "Wil");

    private static final List<String> TAIL_M = List.of(
        "ric", "mund", "wald", "gar", "helm", "stan", "wine", "ward",
        "bert", "mar", "red", "vald", "bold", "grim", "fast", "hard");

    private static final List<String> TAIL_F = List.of(
        "a", "wyn", "gith", "hild", "run", "frid", "lind", "burg",
        "swith", "trud", "gard", "leif");

    private static final List<String> FAMILY_HEAD = List.of(
        "Ash", "Barley", "Cob", "Dun", "Elm", "Ford", "Green", "Hollow",
        "Mill", "Oak", "Stone", "Thatch", "West", "Wold");

    private static final List<String> FAMILY_TAIL = List.of(
        "croft", "field", "gate", "hill", "mere", "row", "stead", "well",
        "wood", "bank");

    private CitizenNames() {
    }

    // Seeded from both halves: the low bits of a Minecraft UUID are not evenly spread, and
    // using them alone gave whole villages the same surname.
    private static long seed(UUID id) {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }

    /**
     * Whether this person is a woman. Half of them are, and always were.
     *
     * <p>The mod has had a sex since the first name was generated — it is the coin flip that
     * chooses between the masculine and feminine name endings. It was simply never exposed, so
     * half the town were women that nothing in the game could tell you about.
     *
     * <p><b>This must stay the FIRST draw of {@link #of}'s sequence.</b> Both methods seed a
     * {@code Random} identically and take {@code nextBoolean()} first, so they agree by
     * construction. Compute the sex any other way — a separate hash, a different salt — and it
     * silently drifts from the name, leaving a Hedda who is a man.
     */
    public static boolean isFeminine(UUID id) {
        return new Random(seed(id)).nextBoolean();
    }

    /** The citizen's full name. Same UUID, same name, every time. */
    public static String of(UUID id) {
        Random rng = new Random(seed(id));
        boolean feminine = rng.nextBoolean();
        String given = pick(rng, HEAD) + pick(rng, feminine ? TAIL_F : TAIL_M);
        String family = pick(rng, FAMILY_HEAD) + pick(rng, FAMILY_TAIL);
        return given + " " + family;
    }

    /** Which of the base skins this citizen wears; stable for the same UUID. */
    public static int skinVariant(UUID id, int variants) {
        return variant(id, 0, variants);
    }

    /**
     * One independent choice out of {@code variants}, stable for the same UUID.
     *
     * <p>The salt is what makes two choices independent. Taking the face as {@code h % 6} and
     * the clothing tint as {@code h % 4} off the SAME {@code h} shares the low bit between
     * them, so an even face forces an even tint — half the combinations never occur and the
     * town comes out in visible pairs. Mixing the salt into the hash first costs nothing and
     * makes each axis its own roll.
     */
    public static int variant(UUID id, int salt, int variants) {
        if (variants <= 1) return 0;
        // A different mix from seed(): this axis has no business correlating with the name.
        long h = id.getMostSignificantBits() * 31L ^ id.getLeastSignificantBits();
        // A cheap avalanche step (the finalizer from xorshift*), so a small salt change
        // moves every bit rather than just the low ones.
        h ^= salt * 0x9E3779B97F4A7C15L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, variants);
    }

    private static String pick(Random rng, List<String> from) {
        return from.get(rng.nextInt(from.size()));
    }
}
