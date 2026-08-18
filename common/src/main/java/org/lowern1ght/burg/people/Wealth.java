package org.lowern1ght.burg.people;

/**
 * How much a person owns, in the four steps a player can actually see on their clothes.
 *
 * <p><b>This is the axis that resolves an argument the project had with itself.</b> The owner
 * wants citizens colossally varied; the material research says a peasant wore the colour the
 * sheep grew, because dye cost money and labour a household in a sunken-floor house did not have.
 * Both are true, and they stop contradicting each other the moment dye is read as <em>wealth</em>:
 *
 * <ul>
 *   <li>{@link #DESTITUTE} — undyed and worn. The cream, grey and moorit of the researched
 *       palette, which becomes the bottom of the range rather than the whole of it.</li>
 *   <li>{@link #POOR} — undyed but sound. Better weave, no holes.</li>
 *   <li>{@link #COMFORTABLE} — properly dyed. Madder, weld: the colours an ordinary household
 *       could reach once it had something to trade.</li>
 *   <li>{@link #RICH} — dyed and trimmed. Woad, a deep madder, braid at the hem.</li>
 * </ul>
 *
 * <p>So a prosperous town is visibly a prosperous town, from a distance, with no number on any
 * screen — and the researched palette is not discarded, it is stratified.
 *
 * <p><b>The consequence for rendering, which is a first for this system:</b> every other visible
 * axis — face, build, hair, headwear — derives from the person's id and therefore needs no
 * syncing at all, because the client can compute it. Wealth changes over time. So this is the
 * first thing about a citizen's appearance that has to travel to the client, and the look layer
 * has to take it as an input rather than roll it.
 *
 * <p>Thresholds are in the smallest coin — gold nuggets, not ingots, since a single unit makes
 * everything cheap cost the same price.
 */
public enum Wealth {

    DESTITUTE(0),
    POOR(16),
    COMFORTABLE(96),
    RICH(512);

    /** Lowest purse, in nuggets, that reads as this tier. */
    private final int floor;

    Wealth(int floor) {
        this.floor = floor;
    }

    public int floor() {
        return floor;
    }

    /**
     * Which tier a purse reads as.
     *
     * <p>Walks down from the top so the thresholds only ever have to be stated once, in the
     * constants above, and adding a tier cannot leave a gap.
     */
    public static Wealth of(int purse) {
        Wealth[] tiers = values();
        for (int i = tiers.length - 1; i >= 0; i--) {
            if (purse >= tiers[i].floor) return tiers[i];
        }
        return DESTITUTE;
    }

    /** Ordinal as a small int for the network, so the wire never carries an enum name. */
    public int tier() {
        return ordinal();
    }

    /** Inverse of {@link #tier()}, clamped rather than throwing on a value from an older save. */
    public static Wealth byTier(int tier) {
        Wealth[] tiers = values();
        return tiers[Math.min(tiers.length - 1, Math.max(0, tier))];
    }
}
