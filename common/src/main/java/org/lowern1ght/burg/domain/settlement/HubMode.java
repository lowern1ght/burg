package org.lowern1ght.burg.domain.settlement;

import java.util.Locale;

/**
 * The act-3 / act-4 hub mode for a single town. Two values, closed set —
 * acts 0–3 sit on {@link #CONSTRUCTION} (the command-console hub of today);
 * act 4+ sits on {@link #SUPPLY} (the read-only intent view, ADR-0019). The
 * transition is the act-4 hand-off.
 *
 * <p>Additive default for worlds saved before this carve lands is
 * {@link #CONSTRUCTION}: a town with no recorded mode reads as CONSTRUCTION,
 * which is what today's command-console behavior already produces. There is
 * no persisted field — the mode is derived from acquisition + queue state at
 * read time (see {@link #SUPPLY}'s ordinate role in the supply-mode wire),
 * so an old world does not need a migration.
 *
 * <p>No Minecraft imports. The enum is a domain value object, the same
 * Minecraft-free discipline {@link Acquisition} established in ADR-0009 and
 * {@link ConstructionQueue} continued in ADR-0011. {@code Town} carries the
 * cross-context facade; {@code TownAnchorBlock} is the engine-edge consumer
 * that logs the mode at right-click (read-only intent surface stays the
 * existing {@code TownHubScreen} widget set until the act-4 PR).
 */
public enum HubMode {

    /**
     * Acts 0–3. The hub is a command console — the player can queue buildings,
     * demolish, assign NPCs. Equivalent to today's {@code TownHubScreen},
     * untouched.
     */
    CONSTRUCTION,

    /**
     * Act 4+. The hub is a window — the player sees what the town intends to
     * build, what it is short of, and supplies items. Construction verbs are
     * not present in this mode (ROADMAP §"Act 4" + VISION §"the hub is a
     * window").
     */
    SUPPLY;

    /**
     * The additive NBT-form default for worlds saved before this carve —
     * same shape as {@link Acquisition#fromNbtOrDefault}. {@code null} and
     * unrecognised strings both read as {@link #CONSTRUCTION}.
     */
    public static HubMode fromNbtOrDefault(String raw) {
        if (raw == null || raw.isEmpty()) return CONSTRUCTION;
        try {
            return HubMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CONSTRUCTION;
        }
    }

    /** Stable NBT form — the {@link #name()} of the enum, uppercase. */
    public String toNbt() {
        return name();
    }

    /** True iff this is the additive default for old saves. */
    public boolean isDefault() {
        return this == CONSTRUCTION;
    }
}